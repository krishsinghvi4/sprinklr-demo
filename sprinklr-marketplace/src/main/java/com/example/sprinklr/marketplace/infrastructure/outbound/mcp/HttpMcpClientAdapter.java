package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.AtlassianOAuthToken;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

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
    private final McpOAuthClient oauthClient;
    private final McpCircuitBreakerFactory circuitBreakerFactory;

    public HttpMcpClientAdapter(
            McpConnectionRepository connectionRepository,
            CredentialVaultPort credentialVault,
            McpCatalogLoader catalogLoader,
            McpAuthStrategyRegistry authStrategyRegistry,
            StreamableHttpMcpClient mcpClient,
            McpOAuthClient oauthClient,
            McpCircuitBreakerFactory circuitBreakerFactory
    ) {
        this.connectionRepository = connectionRepository;
        this.credentialVault = credentialVault;
        this.catalogLoader = catalogLoader;
        this.authStrategyRegistry = authStrategyRegistry;
        this.mcpClient = mcpClient;
        this.oauthClient = oauthClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

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

    private McpInvocationResult executeToolCall(McpInvocation invocation, McpConnectionDocument connection) {
        var catalogEntry = catalogLoader.findById(connection.catalogServerId())
                .orElseThrow(() -> new McpInvocationException(
                        "MCP server configuration not found",
                        "Catalog entry missing for " + connection.catalogServerId()
                ));

        Map<String, String> credentials = credentialVault.decrypt(connection.encryptedCredentials());
        credentials = refreshOAuthIfNeeded(connection, catalogEntry.authType(), credentials);
        Map<String, String> authHeaders = authStrategyRegistry
                .require(catalogEntry.authType())
                .buildAuthHeaders(credentials);

        StreamableHttpMcpClient.McpSession session = resolveSession(connection, catalogEntry, authHeaders);
        String sessionId = session.sessionId();
        String protocolVersion = session.protocolVersion() != null
                ? session.protocolVersion()
                : DEFAULT_PROTOCOL_VERSION;
        JsonNode result;
        try {
            result = mcpClient.callTool(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    sessionId,
                    protocolVersion,
                    invocation.toolName(),
                    invocation.argumentsJson()
            );
        } catch (McpConnectionException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("404")) {
                StreamableHttpMcpClient.McpSession refreshedSession =
                        reinitializeSession(connection, catalogEntry, authHeaders);
                result = mcpClient.callTool(
                        catalogEntry.endpointUrl(),
                        authHeaders,
                        refreshedSession.sessionId(),
                        refreshedSession.protocolVersion(),
                        invocation.toolName(),
                        invocation.argumentsJson()
                );
            } else {
                throw new McpInvocationException(exception.getUserMessage(), exception.getMessage());
            }
        }

        Optional<String> toolError = extractMcpToolError(result);
        if (toolError.isPresent() && isLikelyAuthError(toolError.get())) {
            log.info("[MCP] Tool returned auth error, forcing OAuth refresh connectionId={} tool={}",
                    connection.id(), invocation.toolName());
            credentials = forceRefreshOAuth(connection, credentials);
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
                    invocation.argumentsJson()
            );
            toolError = extractMcpToolError(result);
        }

        if (toolError.isPresent()) {
            String atlassianMessage = toolError.get();
            String userMessage = isLikelyAuthError(atlassianMessage)
                    ? "Jira connection needs to be refreshed. Please disconnect and reconnect Jira from your Profile page."
                    : "MCP tool '" + invocation.toolName() + "' failed: " + atlassianMessage;
            log.warn("[MCP] Tool returned error connectionId={} tool={} args={} error={}",
                    connection.id(), invocation.toolName(), invocation.argumentsJson(), atlassianMessage);
            return failureResult(invocation, userMessage, atlassianMessage);
        }

        String content = formatToolResult(result);
        log.info("[MCP] Tool success connectionId={} tool={} resultLen={}",
                connection.id(), invocation.toolName(), content.length());

        return new McpInvocationResult(invocation.toolCallId(), true, content, null);
    }

    private StreamableHttpMcpClient.McpSession reinitializeSession(
            McpConnectionDocument connection,
            com.example.sprinklr.marketplace.domain.model.McpCatalogEntry catalogEntry,
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

    private StreamableHttpMcpClient.McpSession resolveSession(
            McpConnectionDocument connection,
            com.example.sprinklr.marketplace.domain.model.McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders
    ) {
        String sessionId = connection.mcpSessionId();
        if (sessionId == null || sessionId.isBlank()) {
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

    private Map<String, String> refreshOAuthIfNeeded(
            McpConnectionDocument connection,
            String authType,
            Map<String, String> credentials
    ) {
        if (!"OAUTH_ATLASSIAN".equals(authType)) {
            return credentials;
        }
        AtlassianOAuthToken token = AtlassianOAuthToken.fromCredentialMap(credentials);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new McpInvocationException(
                    "OAuth token missing. Please reconnect.",
                    "Missing access token for connectionId=" + connection.id()
            );
        }
        if (!token.isExpired(Instant.now())) {
            return credentials;
        }
        if (token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new McpInvocationException(
                    "OAuth refresh token missing. Please reconnect.",
                    "Missing refresh token for connectionId=" + connection.id()
            );
        }
        AtlassianOAuthToken refreshed = oauthClient.refreshAccessToken(token.refreshToken());
        String refreshToken = refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()
                ? refreshed.refreshToken()
                : token.refreshToken();
        AtlassianOAuthToken updated = new AtlassianOAuthToken(
                refreshed.accessToken(),
                refreshToken,
                refreshed.expiresAtEpochSec(),
                refreshed.scope(),
                refreshed.tokenType()
        );
        Map<String, String> updatedCredentials = updated.toCredentialMap();
        String encrypted = credentialVault.encrypt(updatedCredentials);
        McpConnectionDocument updatedDocument = new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                connection.serverIdPrefix(),
                encrypted,
                connection.mcpSessionId(),
                connection.mcpProtocolVersion(),
                connection.status(),
                connection.tools(),
                connection.connectedAt(),
                connection.lastError()
        );
        connectionRepository.save(updatedDocument);
        log.info("[MCP] OAuth token refreshed connectionId={}", connection.id());
        return updatedCredentials;
    }

    private Map<String, String> forceRefreshOAuth(
            McpConnectionDocument connection,
            Map<String, String> credentials
    ) {
        AtlassianOAuthToken token = AtlassianOAuthToken.fromCredentialMap(credentials);
        if (token == null || token.refreshToken() == null || token.refreshToken().isBlank()) {
            throw new McpInvocationException(
                    "OAuth refresh token missing. Please reconnect.",
                    "Missing refresh token for connectionId=" + connection.id()
            );
        }
        AtlassianOAuthToken refreshed = oauthClient.refreshAccessToken(token.refreshToken());
        String refreshToken = refreshed.refreshToken() != null && !refreshed.refreshToken().isBlank()
                ? refreshed.refreshToken()
                : token.refreshToken();
        AtlassianOAuthToken updated = new AtlassianOAuthToken(
                refreshed.accessToken(),
                refreshToken,
                refreshed.expiresAtEpochSec(),
                refreshed.scope(),
                refreshed.tokenType()
        );
        Map<String, String> updatedCredentials = updated.toCredentialMap();
        String encrypted = credentialVault.encrypt(updatedCredentials);
        McpConnectionDocument updatedDocument = new McpConnectionDocument(
                connection.id(),
                connection.userId(),
                connection.catalogServerId(),
                connection.serverIdPrefix(),
                encrypted,
                null,
                null,
                connection.status(),
                connection.tools(),
                connection.connectedAt(),
                connection.lastError()
        );
        connectionRepository.save(updatedDocument);
        log.info("[MCP] OAuth token force-refreshed connectionId={}", connection.id());
        return updatedCredentials;
    }

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
