package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.port.outbound.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.CompositeMcpToolResultPostProcessor;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationPreparer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.CompositeMcpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolInvocationContext;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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
    private static final int MAX_TRANSIENT_TOOL_ATTEMPTS = 3;
    private static final long TRANSIENT_RETRY_BACKOFF_MS = 500L;

    private final McpConnectionRepository connectionRepository;
    private final StreamableHttpMcpClient mcpClient;
    private final McpCircuitBreakerFactory circuitBreakerFactory;
    private final McpInvocationPreparer invocationPreparer;
    private final CompositeMcpToolResultPostProcessor resultPostProcessor;
    private final CompositeMcpLocalToolExtension localToolExtension;

    public HttpMcpClientAdapter(
            McpConnectionRepository connectionRepository,
            StreamableHttpMcpClient mcpClient,
            McpCircuitBreakerFactory circuitBreakerFactory,
            McpInvocationPreparer invocationPreparer,
            CompositeMcpToolResultPostProcessor resultPostProcessor,
            CompositeMcpLocalToolExtension localToolExtension
    ) {
        this.connectionRepository = connectionRepository;
        this.mcpClient = mcpClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
        this.invocationPreparer = invocationPreparer;
        this.resultPostProcessor = resultPostProcessor;
        this.localToolExtension = localToolExtension;
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
        McpInvocationPreparer.PreparedInvocation prepared = invocationPreparer.prepare(
                connection,
                invocation.toolName(),
                invocation.argumentsJson()
        );

        if (localToolExtension.handles(prepared.catalogEntry(), invocation.toolName())) {
            McpLocalToolInvocationContext localContext = new McpLocalToolInvocationContext(
                    prepared.catalogEntry(),
                    connection,
                    prepared.credentials(),
                    prepared.authHeaders(),
                    prepared.session(),
                    prepared.argumentsJson()
            );
            String localContent = localToolExtension.invoke(
                    prepared.catalogEntry(),
                    invocation.toolName(),
                    localContext
            );
            log.info("[MCP] Local tool success connectionId={} tool={} resultLen={}",
                    connection.id(), invocation.toolName(), localContent.length());
            return new McpInvocationResult(invocation.toolCallId(), true, localContent, null);
        }

        JsonNode result = callToolWithTransientRetry(
                connection,
                prepared.catalogEntry(),
                prepared.authHeaders(),
                prepared.session(),
                invocation.toolName(),
                prepared.argumentsJson()
        );

        result = retryOnRecoverableToolResultError(
                connection,
                prepared.catalogEntry(),
                prepared.authHeaders(),
                prepared.session(),
                invocation.toolName(),
                prepared.argumentsJson(),
                result
        );

        Optional<String> toolError = extractMcpToolError(result);
        if (toolError.isPresent() && isLikelyAuthError(toolError.get())
                && prepared.catalogEntry().authConfig().isOAuth()) {
            log.info("[MCP] Tool returned auth error, forcing OAuth refresh connectionId={} tool={}",
                    connection.id(), invocation.toolName());
            prepared = invocationPreparer.refreshOAuthAndReprepare(
                    connection,
                    invocation.toolName(),
                    invocation.argumentsJson(),
                    prepared.credentials()
            );
            result = mcpClient.callTool(
                    prepared.catalogEntry().endpointUrl(),
                    prepared.authHeaders(),
                    prepared.session().sessionId(),
                    prepared.session().protocolVersion(),
                    invocation.toolName(),
                    prepared.argumentsJson()
            );
            toolError = extractMcpToolError(result);
        }

        if (toolError.isPresent()) {
            String providerMessage = toolError.get();
            String userMessage = isLikelyAuthError(providerMessage)
                    ? prepared.catalogEntry().displayName() + " connection needs to be refreshed. "
                    + "Please disconnect and reconnect from your Profile page."
                    : "MCP tool '" + invocation.toolName() + "' failed: " + providerMessage;
            log.warn("[MCP] Tool returned error connectionId={} tool={} args={} error={}",
                    connection.id(), invocation.toolName(), invocation.argumentsJson(), providerMessage);
            return failureResult(invocation, userMessage, providerMessage);
        }

        McpInvocationPreparer.PreparedInvocation activePrepared = prepared;
        String content = formatToolResult(result);
        McpInvocationContext context = new McpInvocationContext(
                activePrepared.catalogEntry(),
                connection,
                activePrepared.authHeaders(),
                activePrepared.session(),
                activePrepared.argumentsJson(),
                (toolName, argsJson) -> {
                    JsonNode followUp = callToolWithTransientRetry(
                            connection,
                            activePrepared.catalogEntry(),
                            activePrepared.authHeaders(),
                            activePrepared.session(),
                            toolName,
                            argsJson
                    );
                    Optional<String> followUpError = extractMcpToolError(followUp);
                    if (followUpError.isPresent()) {
                        log.warn("[MCP] Follow-up tool call failed tool={} error={}", toolName, followUpError.get());
                        return null;
                    }
                    return formatToolResult(followUp);
                }
        );
        content = resultPostProcessor.process(
                activePrepared.catalogEntry(),
                invocation.toolName(),
                content,
                context
        );

        log.info("[MCP] Tool success connectionId={} tool={} resultLen={}",
                connection.id(), invocation.toolName(), content.length());
        return new McpInvocationResult(invocation.toolCallId(), true, content, null);
    }

    private JsonNode callToolWithTransientRetry(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders,
            StreamableHttpMcpClient.McpSession session,
            String toolName,
            String argumentsJson
    ) {
        StreamableHttpMcpClient.McpSession activeSession = session;
        RuntimeException lastFailure = null;
        String lastDetail = null;

        for (int attempt = 1; attempt <= MAX_TRANSIENT_TOOL_ATTEMPTS; attempt++) {
            try {
                return mcpClient.callTool(
                        catalogEntry.endpointUrl(),
                        authHeaders,
                        activeSession.sessionId(),
                        activeSession.protocolVersion(),
                        toolName,
                        argumentsJson
                );
            } catch (McpConnectionException | McpDiscoveryException exception) {
                lastFailure = exception;
                lastDetail = exception.getMessage();

                if (!isRecoverableTransportError(lastDetail) || attempt >= MAX_TRANSIENT_TOOL_ATTEMPTS) {
                    break;
                }

                log.info("[MCP] Transient transport error attempt={}/{} connectionId={} tool={} detail={}",
                        attempt, MAX_TRANSIENT_TOOL_ATTEMPTS, connection.id(), toolName, lastDetail);

                if (attempt == 1) {
                    activeSession = invocationPreparer.reinitializeSession(connection, catalogEntry, authHeaders);
                }
                sleepBeforeTransientRetry(attempt);
            }
        }

        throw toInvocationException(lastFailure, lastDetail);
    }

    private JsonNode retryOnRecoverableToolResultError(
            McpConnectionDocument connection,
            McpCatalogEntry catalogEntry,
            Map<String, String> authHeaders,
            StreamableHttpMcpClient.McpSession session,
            String toolName,
            String argumentsJson,
            JsonNode result
    ) {
        StreamableHttpMcpClient.McpSession activeSession = session;
        JsonNode latestResult = result;

        for (int attempt = 1; attempt <= MAX_TRANSIENT_TOOL_ATTEMPTS; attempt++) {
            Optional<String> toolError = extractMcpToolError(latestResult);
            if (toolError.isEmpty() || !isRecoverableTransportError(toolError.get())) {
                return latestResult;
            }
            if (attempt >= MAX_TRANSIENT_TOOL_ATTEMPTS) {
                return latestResult;
            }

            log.info("[MCP] Tool returned recoverable transport error attempt={}/{} connectionId={} tool={}",
                    attempt, MAX_TRANSIENT_TOOL_ATTEMPTS, connection.id(), toolName);

            if (attempt == 1) {
                activeSession = invocationPreparer.reinitializeSession(connection, catalogEntry, authHeaders);
            }
            sleepBeforeTransientRetry(attempt);

            latestResult = mcpClient.callTool(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    activeSession.sessionId(),
                    activeSession.protocolVersion(),
                    toolName,
                    argumentsJson
            );
        }

        return latestResult;
    }

    private static McpInvocationException toInvocationException(RuntimeException failure, String detail) {
        if (failure instanceof McpConnectionException connectionException) {
            return new McpInvocationException(connectionException.getUserMessage(), detail);
        }
        if (failure instanceof McpDiscoveryException discoveryException) {
            String userMessage = isRecoverableTransportError(detail)
                    ? "Temporary connection issue with the server — please try again"
                    : discoveryException.getUserMessage();
            return new McpInvocationException(userMessage, detail);
        }
        return new McpInvocationException("Tool invocation failed — please try again", detail);
    }

    private static void sleepBeforeTransientRetry(int attempt) {
        try {
            Thread.sleep(TRANSIENT_RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
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
