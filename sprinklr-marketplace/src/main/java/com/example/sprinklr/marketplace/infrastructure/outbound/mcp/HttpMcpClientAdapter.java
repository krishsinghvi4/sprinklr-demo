package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.AtlassianJiraToolArgumentNormalizer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueTypeCreateRequirementsCache;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueTypeFieldShapeCache;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueTypeMetadataProcessor;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.application.service.mcp.McpOAuthTokenRefreshService;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * MCP server adapter that executes tool calls using stored credentials and sessions.
 * Handles OAuth refresh, session reinitialization, and tool error normalization.
 */
@Component
public class HttpMcpClientAdapter implements McpServerPort {

    private static final Logger log = LoggerFactory.getLogger(HttpMcpClientAdapter.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";

    private final McpConnectionRepository connectionRepository;
    private final CredentialVaultPort credentialVault;
    private final McpCatalogLoader catalogLoader;
    private final McpAuthStrategyRegistry authStrategyRegistry;
    private final StreamableHttpMcpClient mcpClient;
    private final McpOAuthTokenRefreshService oauthTokenRefreshService;
    private final McpCircuitBreakerFactory circuitBreakerFactory;
    private final AtlassianJiraToolArgumentNormalizer atlassianJiraToolArgumentNormalizer;
    private final JiraIssueTypeFieldShapeCache jiraIssueTypeFieldShapeCache;
    private final JiraIssueTypeCreateRequirementsCache jiraIssueTypeCreateRequirementsCache;

    public HttpMcpClientAdapter(
            McpConnectionRepository connectionRepository,
            CredentialVaultPort credentialVault,
            McpCatalogLoader catalogLoader,
            McpAuthStrategyRegistry authStrategyRegistry,
            StreamableHttpMcpClient mcpClient,
            McpOAuthTokenRefreshService oauthTokenRefreshService,
            McpCircuitBreakerFactory circuitBreakerFactory,
            AtlassianJiraToolArgumentNormalizer atlassianJiraToolArgumentNormalizer,
            JiraIssueTypeFieldShapeCache jiraIssueTypeFieldShapeCache,
            JiraIssueTypeCreateRequirementsCache jiraIssueTypeCreateRequirementsCache
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialVault = credentialVault;
        this.catalogLoader = catalogLoader;
        this.authStrategyRegistry = authStrategyRegistry;
        this.mcpClient = mcpClient;
        this.oauthTokenRefreshService = oauthTokenRefreshService;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.atlassianJiraToolArgumentNormalizer = atlassianJiraToolArgumentNormalizer;
        this.jiraIssueTypeFieldShapeCache = jiraIssueTypeFieldShapeCache;
        this.jiraIssueTypeCreateRequirementsCache = jiraIssueTypeCreateRequirementsCache;
    }

    /**
     * Executes an MCP tool invocation for a specific user connection.
     */
    @Override
    public McpInvocationResult invoke(McpInvocation invocation) {
        String connectionId = invocation.serverId();
        log.info("[MCP] Invoking tool connectionId={} tool={} arguments={}",
                connectionId, invocation.toolName(), invocation.argumentsJson());

        Optional<McpConnectionDocument> connectionOpt = connectionRepository.findById(connectionId);
        if (connectionOpt.isEmpty()) {
            log.warn("[MCP] Connection not found connectionId={}", connectionId);
            return failureResult(
                    invocation,
                    "MCP server connection not found",
                    "connectionId=" + connectionId
            );
        }

        McpConnectionDocument connection = connectionOpt.get();
        CircuitBreaker circuitBreaker = circuitBreakerFactory.forConnection(connectionId);

        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    executeToolCall(invocation, connection)
            ).get();
        } catch (CallNotPermittedException exception) {
            log.warn("[MCP] Circuit open connectionId={} tool={}", connectionId, invocation.toolName());
            return failureResult(
                    invocation,
                    "MCP server temporarily unavailable",
                    "circuit open for connectionId=" + connectionId
            );
        } catch (McpInvocationException exception) {
            log.warn("[MCP] Tool invocation failed connectionId={} tool={} args={}: {}",
                    connectionId, invocation.toolName(), invocation.argumentsJson(), exception.getMessage());
            return failureResult(
                    invocation,
                    exception.getUserMessage(),
                    exception.getMessage()
            );
        } catch (Exception exception) {
            log.error("[MCP] Unexpected tool invocation error connectionId={} tool={} args={}: {}",
                    connectionId, invocation.toolName(), invocation.argumentsJson(), exception.getMessage());
            return failureResult(
                    invocation,
                    "Tool invocation failed — please try again",
                    exception.getMessage()
            );
        }
    }

    private McpInvocationResult failureResult(McpInvocation invocation, String userMessage, String detail) {
        String message = "Tool '" + invocation.toolName() + "' failed. Request args: "
                + invocation.argumentsJson() + ". Detail: " + detail;
        return new McpInvocationResult(invocation.toolCallId(), false, null, message);
    }

    /**
     * Resolves auth/session, calls the MCP server, and normalizes any tool errors.
     */
    private McpInvocationResult executeToolCall(McpInvocation invocation, McpConnectionDocument connection) {
        var catalogEntry = catalogLoader.findById(connection.catalogServerId())
                .orElseThrow(() -> new McpInvocationException(
                        "MCP server configuration not found",
                        "Catalog entry missing for " + connection.catalogServerId()
                ));

        Map<String, String> credentials = credentialVault.decrypt(connection.encryptedCredentials());
        credentials = oauthTokenRefreshService.refreshIfNeeded(connection, catalogEntry, credentials);
        Map<String, String> authHeaders = authStrategyRegistry
                .require(catalogEntry.authType())
                .buildAuthHeaders(credentials);

        StreamableHttpMcpClient.McpSession session = resolveSession(connection, catalogEntry, authHeaders);
        String argumentsJson = atlassianJiraToolArgumentNormalizer.normalize(
                invocation.toolName(),
                invocation.argumentsJson(),
                connection.id()
        );
        JsonNode result = callToolWithSessionRecovery(
                connection,
                catalogEntry,
                authHeaders,
                session,
                invocation.toolName(),
                argumentsJson
        );

        Optional<String> toolError = extractMcpToolError(result);
        if (toolError.isPresent() && isRecoverableTransportError(toolError.get())) {
            log.info("[MCP] Tool returned recoverable transport error, re-initializing connectionId={} tool={}",
                    connection.id(), invocation.toolName());
            StreamableHttpMcpClient.McpSession refreshedSession =
                    reinitializeSession(connection, catalogEntry, authHeaders);
            result = mcpClient.callTool(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    refreshedSession.sessionId(),
                    refreshedSession.protocolVersion(),
                    invocation.toolName(),
                    argumentsJson
            );
            toolError = extractMcpToolError(result);
        }
        if (toolError.isPresent() && isLikelyAuthError(toolError.get())) {
            log.info("[MCP] Tool returned auth error, forcing OAuth refresh connectionId={} tool={}",
                    connection.id(), invocation.toolName());
            credentials = oauthTokenRefreshService.forceRefresh(connection, catalogEntry, credentials);
            authHeaders = authStrategyRegistry
                    .require(catalogEntry.authType())
                    .buildAuthHeaders(credentials);
            StreamableHttpMcpClient.McpSession refreshedSession =
                    reinitializeSession(connection, catalogEntry, authHeaders);
            result = mcpClient.callTool(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    refreshedSession.sessionId(),
                    refreshedSession.protocolVersion(),
                    invocation.toolName(),
                    argumentsJson
            );
            toolError = extractMcpToolError(result);
        }

        if (toolError.isPresent()) {
            String providerMessage = toolError.get();
            String userMessage = isLikelyAuthError(providerMessage)
                    ? catalogEntry.displayName() + " connection needs to be refreshed. "
                    + "Please disconnect and reconnect from your Profile page."
                    : "MCP tool '" + invocation.toolName() + "' failed: " + providerMessage;
            log.warn("[MCP] Tool returned error connectionId={} tool={} args={} error={}",
                    connection.id(), invocation.toolName(), invocation.argumentsJson(), providerMessage);
            return failureResult(invocation, userMessage, providerMessage);
        }

        String content = formatToolResult(result);
        if (isJiraMetadataTool(invocation.toolName())) {
            content = processJiraIssueTypeMetadata(
                    content,
                    connection,
                    catalogEntry,
                    authHeaders,
                    session,
                    invocation.toolName(),
                    argumentsJson
            );
        }
        log.info("[MCP] Tool success connectionId={} tool={} resultLen={}",
                connection.id(), invocation.toolName(), content.length());

        return new McpInvocationResult(invocation.toolCallId(), true, content, null);
    }

    /**
     * Invokes a tool and re-initializes the MCP session once on stale-session or transient transport errors.
     */
    private JsonNode callToolWithSessionRecovery(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders,
            StreamableHttpMcpClient.McpSession session,
            String toolName,
            String argumentsJson
    ) {
        try {
            return mcpClient.callTool(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    session.sessionId(),
                    session.protocolVersion(),
                    toolName,
                    argumentsJson
            );
        } catch (McpConnectionException exception) {
            return retryAfterSessionReinit(
                    connection, catalogEntry, authHeaders, toolName, argumentsJson, exception.getMessage(), exception);
        } catch (McpDiscoveryException exception) {
            return retryAfterSessionReinit(
                    connection, catalogEntry, authHeaders, toolName, argumentsJson, exception.getMessage(), exception);
        }
    }

    private JsonNode retryAfterSessionReinit(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders,
            String toolName,
            String argumentsJson,
            String detail,
            RuntimeException original
    ) {
        if (!isRecoverableTransportError(detail)) {
            if (original instanceof McpConnectionException connectionException) {
                throw new McpInvocationException(connectionException.getUserMessage(), detail);
            }
            McpDiscoveryException discoveryException = (McpDiscoveryException) original;
            throw new McpInvocationException(discoveryException.getUserMessage(), detail);
        }
        log.info("[MCP] Recoverable transport error, re-initializing connectionId={} tool={} detail={}",
                connection.id(), toolName, detail);
        StreamableHttpMcpClient.McpSession refreshedSession =
                reinitializeSession(connection, catalogEntry, authHeaders);
        return mcpClient.callTool(
                catalogEntry.endpointUrl(),
                authHeaders,
                refreshedSession.sessionId(),
                refreshedSession.protocolVersion(),
                toolName,
                argumentsJson
        );
    }

    static boolean isRecoverableTransportError(String detail) {
        if (detail == null || detail.isBlank()) {
            return false;
        }
        String lower = detail.toLowerCase();
        return lower.contains("404")
                || lower.contains("econnreset")
                || lower.contains("econnrefused")
                || lower.contains("etimedout")
                || lower.contains("socket hang up")
                || lower.contains("connection reset")
                || lower.contains("connection refused")
                || lower.contains("server not initialized")
                || lower.contains("session expired")
                || lower.contains("invalid session")
                || lower.contains("unknown session");
    }

    /**
     * Re-initializes MCP session metadata and persists it.
     */
    private StreamableHttpMcpClient.McpSession reinitializeSession(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders
    ) {
        log.info("[MCP] Re-initializing session connectionId={}", connection.id());
        StreamableHttpMcpClient.McpSession session = mcpClient.initialize(catalogEntry.endpointUrl(), authHeaders);
        McpConnectionDocument updated = new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                connection.serverIdPrefix(),
                connection.encryptedCredentials(),
                session.sessionId(),
                session.protocolVersion(),
                connection.status(),
                connection.tools(),
                connection.connectedAt(),
                connection.lastError()
        );
        connectionRepository.save(updated);
        return session;
    }

    /**
     * Returns the current session or initializes a new one if missing.
     */
    private StreamableHttpMcpClient.McpSession resolveSession(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders
    ) {
        String sessionId = connection.mcpSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            log.info("[MCP] No stored session, initializing connectionId={} catalog={}",
                    connection.id(), connection.catalogServerId());
            StreamableHttpMcpClient.McpSession session = mcpClient.initialize(catalogEntry.endpointUrl(), authHeaders);
            McpConnectionDocument updated = new McpConnectionDocument(
                    connection.id(),
                    connection.userId(),
                    connection.catalogServerId(),
                    connection.serverIdPrefix(),
                    connection.encryptedCredentials(),
                    session.sessionId(),
                    session.protocolVersion(),
                    connection.status(),
                    connection.tools(),
                    connection.connectedAt(),
                    connection.lastError()
            );
            connectionRepository.save(updated);
            return session;
        }
        String protocolVersion = connection.mcpProtocolVersion() != null
                ? connection.mcpProtocolVersion()
                : DEFAULT_PROTOCOL_VERSION;
        return new StreamableHttpMcpClient.McpSession(sessionId, protocolVersion);
    }

    /**
     * Attempts to extract a tool error from standard MCP response shapes.
     */
    private Optional<String> extractMcpToolError(JsonNode result) {
        if (result == null || result.isMissingNode()) {
            return Optional.of("Empty MCP tool result");
        }
        if (result.has("isError") && result.get("isError").asBoolean(false)) {
            return Optional.of(extractErrorMessageFromContent(result));
        }
        String content = formatToolResult(result);
        if (content.contains("\"error\":true") || content.contains("\"error\": true")) {
            try {
                JsonNode parsed = OBJECT_MAPPER.readTree(content);
                if (parsed.has("error") && parsed.get("error").asBoolean(false) && parsed.has("message")) {
                    return Optional.of(parsed.get("message").asText(content));
                }
            } catch (Exception ignored) {
                return Optional.of(content);
            }
        }
        return Optional.empty();
    }

    private String extractErrorMessageFromContent(JsonNode result) {
        JsonNode contentNode = result.path("content");
        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                if (item.has("text")) {
                    String text = item.path("text").asText("");
                    try {
                        JsonNode parsed = OBJECT_MAPPER.readTree(text);
                        if (parsed.has("message")) {
                            return parsed.get("message").asText(text);
                        }
                    } catch (Exception ignored) {
                        return text;
                    }
                    return text;
                }
            }
        }
        return "Unknown MCP tool error";
    }

    private String processJiraIssueTypeMetadata(
            String rawContent,
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders,
            StreamableHttpMcpClient.McpSession session,
            String toolName,
            String argumentsJson
    ) {
        try {
            JsonNode firstPage = OBJECT_MAPPER.readTree(rawContent);
            List<JsonNode> additionalPages = new ArrayList<>();
            int fetchedCount = JiraIssueTypeMetadataProcessor.analyzeSinglePage(firstPage).fetchedFieldCount();
            int startAt = JiraIssueTypeMetadataProcessor.nextStartAt(firstPage, fetchedCount);
            JsonNode latestPage = firstPage;
            int pageGuard = 0;

            while (JiraIssueTypeMetadataProcessor.needsMorePages(latestPage, fetchedCount) && pageGuard < 25) {
                pageGuard++;
                String pagedArgs = withMetadataPagination(argumentsJson, startAt);
                log.info("[JiraMetadata] Fetching paginated field metadata connectionId={} startAt={}",
                        connection.id(), startAt);
                JsonNode nextResult = callToolWithSessionRecovery(
                        connection,
                        catalogEntry,
                        authHeaders,
                        session,
                        toolName,
                        pagedArgs
                );
                Optional<String> pageError = extractMcpToolError(nextResult);
                if (pageError.isPresent()) {
                    log.warn("[JiraMetadata] Paginated metadata fetch failed startAt={}: {}",
                            startAt, pageError.get());
                    break;
                }
                JsonNode nextPage = OBJECT_MAPPER.readTree(formatToolResult(nextResult));
                additionalPages.add(nextPage);
                fetchedCount += JiraIssueTypeMetadataProcessor.analyzeSinglePage(nextPage).fetchedFieldCount();
                latestPage = nextPage;
                startAt = JiraIssueTypeMetadataProcessor.nextStartAt(nextPage, fetchedCount);
            }

            JiraIssueTypeMetadataProcessor.ProcessedMetadata processed = additionalPages.isEmpty()
                    ? JiraIssueTypeMetadataProcessor.analyzeSinglePage(firstPage)
                    : JiraIssueTypeMetadataProcessor.mergePages(firstPage, additionalPages);

            jiraIssueTypeFieldShapeCache.put(
                    connection.id(),
                    JiraIssueTypeMetadataProcessor.fieldShapes(processed)
            );

            JiraIssueTypeMetadataProcessor.parseCreateScope(argumentsJson).ifPresent(scope -> {
                List<JiraIssueTypeMetadataProcessor.RequiredField> requiredFields =
                        JiraIssueTypeMetadataProcessor.requiredUserInputFields(processed);
                LinkedHashSet<String> issueTypeKeys = new LinkedHashSet<>(scope.issueTypeLookupKeys());
                JiraIssueTypeMetadataProcessor.extractIssueTypeIdentity(processed).ifPresent(identity -> {
                    if (identity.name() != null) {
                        issueTypeKeys.add(identity.name());
                    }
                    if (identity.id() != null) {
                        issueTypeKeys.add(identity.id());
                    }
                });
                jiraIssueTypeCreateRequirementsCache.putAliases(
                        connection.id(),
                        scope.projectKey(),
                        List.copyOf(issueTypeKeys),
                        requiredFields
                );
            });

            return JiraIssueTypeMetadataProcessor.summarize(processed).orElse(rawContent);
        } catch (Exception exception) {
            log.warn("[JiraMetadata] Failed to process metadata for connectionId={}: {}",
                    connection.id(), exception.getMessage());
            return rawContent;
        }
    }

    private String withMetadataPagination(String argumentsJson, int startAt) throws Exception {
        JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
        if (!root.isObject()) {
            ObjectNode args = OBJECT_MAPPER.createObjectNode();
            args.put("startAt", startAt);
            return OBJECT_MAPPER.writeValueAsString(args);
        }
        ObjectNode args = (ObjectNode) root;
        args.put("startAt", startAt);
        if (!args.has("maxResults")) {
            args.put("maxResults", 200);
        }
        return OBJECT_MAPPER.writeValueAsString(args);
    }

    private static boolean isJiraMetadataTool(String toolName) {
        return bareToolName(toolName) != null
                && "getJiraIssueTypeMetaWithFields".equals(bareToolName(toolName));
    }

    private static String bareToolName(String toolName) {
        if (toolName == null) {
            return null;
        }
        return toolName.contains(".") ? toolName.substring(toolName.indexOf('.') + 1) : toolName;
    }

    private boolean isLikelyAuthError(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("connection settings")
                || lower.contains("verify your connection")
                || lower.contains("authentication")
                || lower.contains("unauthorized")
                || lower.contains("permission")
                || lower.contains("reconnect");
    }

    /**
     * Flattens MCP tool output into a string for LLM tool result messages.
     */
    private String formatToolResult(JsonNode result) {
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
