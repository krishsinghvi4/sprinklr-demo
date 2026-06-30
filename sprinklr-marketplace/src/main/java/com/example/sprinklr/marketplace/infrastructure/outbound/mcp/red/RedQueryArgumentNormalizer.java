package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpToolArgumentNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Repairs common LLM mistakes when calling RED Elasticsearch execute tools.
 */
@Component
public class RedQueryArgumentNormalizer implements McpToolArgumentNormalizer {

    private static final Logger log = LoggerFactory.getLogger(RedQueryArgumentNormalizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String RED_PREFIX = "red";
    private static final Set<String> ELASTICSEARCH_EXECUTE_TOOLS = Set.of("red_execute_elastic_search_query");

    private final RedEsSampleFieldContext sampleFieldContext;

    public RedQueryArgumentNormalizer(RedEsSampleFieldContext sampleFieldContext) {
        this.sampleFieldContext = sampleFieldContext;
    }

    @Override
    public boolean supports(McpCatalogEntry entry, String toolName) {
        return RED_PREFIX.equals(entry.serverIdPrefix()) && supports(toolName);
    }

    public boolean supports(String toolName) {
        String bare = bareToolName(toolName);
        return bare != null && ELASTICSEARCH_EXECUTE_TOOLS.contains(bare);
    }

    @Override
    public String normalize(McpCatalogEntry entry, String toolName, String argumentsJson, String connectionId) {
        return normalize(toolName, argumentsJson);
    }

    public String normalize(String toolName, String argumentsJson) {
        if (!supports(toolName) || argumentsJson == null || argumentsJson.isBlank()) {
            return argumentsJson;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            if (!root.isObject()) {
                return argumentsJson;
            }
            ObjectNode args = (ObjectNode) root;
            JsonNode queryNode = args.get("query");
            if (queryNode != null && queryNode.isObject()) {
                args.put("query", OBJECT_MAPPER.writeValueAsString(queryNode));
                log.info("[MCP] Normalized RED ES tool={} stringified query object", toolName);
            }
            JsonNode queryStringNode = args.get("query");
            if (queryStringNode != null && queryStringNode.isTextual()) {
                sampleFieldContext.current().ifPresent(catalog -> {
                    String original = queryStringNode.asText();
                    String fixed = RedEsQueryFieldCorrector.correctQueryFields(original, catalog);
                    if (!fixed.equals(original)) {
                        args.put("query", fixed);
                        log.info("[MCP] Normalized RED ES tool={} corrected filter fields using sample schema",
                                toolName);
                    }
                });
            }
            return OBJECT_MAPPER.writeValueAsString(args);
        } catch (Exception exception) {
            log.warn("[MCP] Failed to normalize RED ES args for tool={}: {}", toolName, exception.getMessage());
            return argumentsJson;
        }
    }

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
    }
}
