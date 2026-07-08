package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.port.outbound.RedSampleQueryCachePort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolInvocationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Caches full RED sample query results and serves them on subsequent identical scope-arg calls.
 */
@Component
public class RedSampleQueryCacheLocalTool implements McpLocalToolExtension {

    private static final Logger log = LoggerFactory.getLogger(RedSampleQueryCacheLocalTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RED_PREFIX = "red";
    private static final String EMPTY_SCHEMA_MESSAGE =
            "Sample returned no filterable fields for this scope — verify partnerId, serverType, "
                    + "indexName/collectionName, and env.";

    private final RedSampleQueryCachePort cachePort;
    private final RedSampleQueryCacheKeyBuilder keyBuilder;
    private final StreamableHttpMcpClient mcpClient;
    private final McpProperties mcpProperties;

    public RedSampleQueryCacheLocalTool(
            RedSampleQueryCachePort cachePort,
            RedSampleQueryCacheKeyBuilder keyBuilder,
            StreamableHttpMcpClient mcpClient,
            McpProperties mcpProperties
    ) {
        this.cachePort = cachePort;
        this.keyBuilder = keyBuilder;
        this.mcpClient = mcpClient;
        this.mcpProperties = mcpProperties;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return RED_PREFIX.equals(entry.serverIdPrefix());
    }

    @Override
    public List<McpTool> toolDefinitions(McpCatalogEntry entry, String connectionId) {
        return List.of();
    }

    @Override
    public boolean handles(McpCatalogEntry entry, String bareToolName) {
        return supports(entry) && keyBuilder.isSampleTool(bareToolName);
    }

    @Override
    public String invoke(McpCatalogEntry entry, String bareToolName, McpLocalToolInvocationContext context) {
        String userId = context.connection().userId();
        String connectionId = context.connection().id();
        int documentLimit = documentLimit();
        int schemaDiscoveryLimit = schemaDiscoveryLimit();

        Optional<String> cached = cachePort.find(userId, connectionId, bareToolName, context.argumentsJson());
        if (cached.isPresent()) {
            String schemaContent = cached.get();
            String displayContent = RedSampleResponseTrimmer.trimToLimit(schemaContent, documentLimit);
            if (RedSampleFieldPathIndex.hasFilterablePaths(schemaContent)) {
                log.info("[RedSampleCache] Serving cached sample connectionId={} tool={}", connectionId, bareToolName);
                return prepareForLlm(
                        schemaContent,
                        displayContent,
                        schemaDiscoveryLimit,
                        true,
                        connectionId,
                        bareToolName
                );
            }
            log.warn("[RedSampleCache] Stale cached sample without filterable paths — re-fetching connectionId={} tool={}",
                    connectionId, bareToolName);
            cachePort.delete(userId, connectionId, bareToolName, context.argumentsJson());
        }

        log.info("[RedSampleCache] Cache miss — calling RED MCP connectionId={} tool={}", connectionId, bareToolName);
        String enrichedArgs = RedSampleQueryArgsEnricher.enrich(
                bareToolName,
                context.argumentsJson(),
                schemaDiscoveryLimit
        );
        String content = callRemoteSample(context, bareToolName, enrichedArgs);
        String displayContent = RedSampleResponseTrimmer.trimToLimit(content, documentLimit);
        RedSampleSchemaIndex indexed = indexOrThrow(content, displayContent, schemaDiscoveryLimit);
        cachePort.save(userId, connectionId, bareToolName, context.argumentsJson(), content);
        log.info("[RedSampleCache] Returning indexed sample connectionId={} tool={} cacheHit={} pathCount={}",
                connectionId, bareToolName, false, indexed.paths().size());
        return indexed.indexedContent();
    }

    private String prepareForLlm(
            String schemaContent,
            String displayContent,
            int schemaDiscoveryLimit,
            boolean cacheHit,
            String connectionId,
            String bareToolName
    ) {
        RedSampleSchemaIndex indexed = indexOrThrow(schemaContent, displayContent, schemaDiscoveryLimit);
        log.info("[RedSampleCache] Returning indexed sample connectionId={} tool={} cacheHit={} pathCount={}",
                connectionId, bareToolName, cacheHit, indexed.paths().size());
        return indexed.indexedContent();
    }

    private RedSampleSchemaIndex indexOrThrow(
            String schemaContent,
            String displayContent,
            int schemaDiscoveryLimit
    ) {
        int documentsReturned = RedSampleResponseTrimmer.countDocuments(schemaContent);
        RedSampleSchemaIndex indexed = RedSampleFieldPathIndex.indexForLlm(
                schemaContent,
                displayContent,
                schemaDiscoveryLimit,
                documentsReturned
        );
        if (indexed.paths().isEmpty()) {
            throw new McpInvocationException(EMPTY_SCHEMA_MESSAGE, "empty_schema");
        }
        return indexed;
    }

    private int documentLimit() {
        if (mcpProperties.getRed() == null || mcpProperties.getRed().getSampleQuery() == null) {
            return 2;
        }
        int configured = mcpProperties.getRed().getSampleQuery().getDocumentLimit();
        return configured > 0 ? configured : 2;
    }

    private int schemaDiscoveryLimit() {
        if (mcpProperties.getRed() == null || mcpProperties.getRed().getSampleQuery() == null) {
            return 5;
        }
        int configured = mcpProperties.getRed().getSampleQuery().getSchemaDiscoveryLimit();
        return configured > 0 ? configured : 5;
    }

    private String callRemoteSample(
            McpLocalToolInvocationContext context,
            String bareToolName,
            String argumentsJson
    ) {
        if (context.session() == null
                || context.session().sessionId() == null
                || context.session().sessionId().isBlank()) {
            throw new McpInvocationException(
                    "RED MCP session is missing — please reconnect RED from your Profile page",
                    "Missing MCP session for " + bareToolName
            );
        }

        JsonNode result;
        try {
            result = mcpClient.callTool(
                    context.catalogEntry().endpointUrl(),
                    context.authHeaders(),
                    context.session().sessionId(),
                    context.session().protocolVersion(),
                    bareToolName,
                    argumentsJson
            );
        } catch (Exception exception) {
            throw new McpInvocationException(
                    "Failed to call RED sample query — please try again",
                    exception.getMessage()
            );
        }

        if (result != null && result.path("isError").asBoolean(false)) {
            throw new McpInvocationException(
                    "RED sample query failed: " + extractMcpErrorMessage(result),
                    extractMcpErrorMessage(result)
            );
        }

        String content = formatToolResult(result);
        if (content.contains("\"error\":true") || content.contains("\"error\": true")) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(content);
                if (parsed.has("error") && parsed.get("error").asBoolean(false)) {
                    String message = parsed.path("message").asText(content);
                    throw new McpInvocationException("RED sample query failed: " + message, message);
                }
            } catch (McpInvocationException exception) {
                throw exception;
            } catch (Exception ignored) {
                throw new McpInvocationException("RED sample query failed: " + content, content);
            }
        }
        return content;
    }

    private static String extractMcpErrorMessage(JsonNode result) {
        JsonNode contentNode = result.path("content");
        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                if (item.has("text")) {
                    return item.path("text").asText("Unknown MCP tool error");
                }
            }
        }
        return "Unknown MCP tool error";
    }

    private static String formatToolResult(JsonNode result) {
        if (result.has("content") && result.get("content").isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode item : result.get("content")) {
                if (item.has("text")) {
                    if (text.length() > 0) {
                        text.append('\n');
                    }
                    text.append(item.path("text").asText());
                }
            }
            if (text.length() > 0) {
                return text.toString();
            }
        }
        if (result.has("content")) {
            try {
                return OBJECT_MAPPER.writeValueAsString(result.path("content"));
            } catch (Exception exception) {
                return result.path("content").toString();
            }
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception exception) {
            return result.toString();
        }
    }
}
