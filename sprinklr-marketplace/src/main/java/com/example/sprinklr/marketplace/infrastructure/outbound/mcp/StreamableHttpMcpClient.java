package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Low-level MCP JSON-RPC client used for initialize, tools/list, and tools/call.
 * Handles session headers and supports streaming responses via HTTP.
 */
@Component
public class StreamableHttpMcpClient {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpMcpClient.class);
    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    private static final String MCP_PROTOCOL_VERSION_HEADER = "MCP-Protocol-Version";
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 2;
    private static final long DEFAULT_RETRY_AFTER_MS = 1_000L;

    private final WebClient webClient;
    private final AtomicLong requestId = new AtomicLong(1);

    public StreamableHttpMcpClient(@Qualifier("mcpWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Sends an MCP initialize request and returns the session metadata.
     */
    public McpSession initialize(String endpointUrl, Map<String, String> authHeaders) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.set("capabilities", OBJECT_MAPPER.createObjectNode());
        ObjectNode clientInfo = OBJECT_MAPPER.createObjectNode();
        clientInfo.put("name", "sprinklr-marketplace");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        McpHttpResponse response = sendRequest(
                endpointUrl,
                authHeaders,
                null,
                null,
                "initialize",
                params
        );

        String sessionId = response.sessionId();
        String protocolVersion = PROTOCOL_VERSION;
        if (response.result() != null && response.result().has("protocolVersion")) {
            protocolVersion = response.result().path("protocolVersion").asText(PROTOCOL_VERSION);
        }

        sendNotification(endpointUrl, authHeaders, sessionId, protocolVersion, "notifications/initialized");

        log.info("[MCP] Initialized endpoint={} sessionPresent={} protocolVersion={}",
                endpointUrl, sessionId != null, protocolVersion);
        return new McpSession(sessionId, protocolVersion);
    }

    /**
     * Lists tools from the MCP server using the active session.
     */
    public JsonNode listTools(
            String endpointUrl,
            Map<String, String> authHeaders,
            McpSession session
    ) {
        return sendRequest(
                endpointUrl,
                authHeaders,
                session.sessionId(),
                session.protocolVersion(),
                "tools/list",
                OBJECT_MAPPER.createObjectNode()
        ).result();
    }

    /**
     * Executes a single tool call by name and arguments JSON.
     */
    public JsonNode callTool(
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion,
            String toolName,
            String argumentsJson
    ) {
        ObjectNode params = OBJECT_MAPPER.createObjectNode();
        params.put("name", toolName);
        try {
            params.set("arguments", OBJECT_MAPPER.readTree(argumentsJson));
        } catch (Exception exception) {
            params.set("arguments", OBJECT_MAPPER.createObjectNode());
        }
        return sendRequest(
                endpointUrl,
                authHeaders,
                sessionId,
                protocolVersion,
                "tools/call",
                params
        ).result();
    }

    /**
     * Sends MCP notifications that don't expect a response body.
     */
    private void sendNotification(
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion,
            String method
    ) {
        try {
            ObjectNode body = OBJECT_MAPPER.createObjectNode();
            body.put("jsonrpc", "2.0");
            body.put("method", method);
            body.set("params", OBJECT_MAPPER.createObjectNode());

            webClient.post()
                    .uri(endpointUrl)
                    .headers(headers -> applyHeaders(headers, authHeaders, sessionId, protocolVersion))
                    .bodyValue(toJson(body))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception exception) {
            log.warn("[MCP] Notification {} failed: {}", method, exception.getMessage());
        }
    }

    /**
     * Sends a JSON-RPC request and parses the MCP response/result.
     */
    private McpHttpResponse sendRequest(
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion,
            String method,
            JsonNode params
    ) {
        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", requestId.getAndIncrement());
        body.put("method", method);
        body.set("params", params);

        String requestJson = toJson(body);
        log.info("[MCP-HTTP] POST method={} endpoint={} sessionPresent={} body={}",
                method, endpointUrl, sessionId != null && !sessionId.isBlank(), requestJson);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return webClient.post()
                        .uri(endpointUrl)
                        .headers(headers -> applyHeaders(headers, authHeaders, sessionId, protocolVersion))
                        .bodyValue(requestJson)
                        .exchangeToMono(response -> response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(responseBody -> {
                                    if (response.statusCode().value() == 429) {
                                        long retryAfterMs = retryAfterMillis(response.headers().asHttpHeaders());
                                        throw new McpConnectionException(
                                                "MCP server is rate limited — please retry",
                                                "MCP HTTP 429 retryAfterMs=" + retryAfterMs);
                                    }
                                    return toMcpHttpResponse(response, responseBody, method);
                                }))
                        .block();
            } catch (McpConnectionException exception) {
                if (attempt < MAX_ATTEMPTS && isRateLimitException(exception)) {
                    long retryAfterMs = retryAfterMillisFromDetail(exception.getMessage());
                    log.warn("[MCP-HTTP] Rate limited (HTTP 429). Waiting {}ms before retry", retryAfterMs);
                    sleep(retryAfterMs);
                    continue;
                }
                throw exception;
            } catch (McpDiscoveryException exception) {
                throw exception;
            } catch (WebClientResponseException exception) {
                log.error("[MCP-HTTP] POST failed method={} status={} body={}",
                        method, exception.getStatusCode().value(), truncate(exception.getResponseBodyAsString()));
                if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                    throw new McpConnectionException(
                            "Invalid credentials — check email and API token",
                            "MCP auth failed: " + exception.getMessage());
                }
                throw new McpConnectionException(
                        "Could not reach MCP server — try again later",
                        "MCP HTTP error: " + exception.getMessage());
            } catch (Exception exception) {
                throw new McpConnectionException(
                        "Could not reach MCP server — try again later",
                        "MCP request error: " + exception.getMessage());
            }
        }

        throw new McpConnectionException(
                "Could not reach MCP server — try again later",
                "MCP request failed after retries");
    }

    private McpHttpResponse toMcpHttpResponse(
            org.springframework.web.reactive.function.client.ClientResponse response,
            String responseBody,
            String method
    ) {
        HttpStatusCode status = response.statusCode();
        String sessionId = response.headers().asHttpHeaders().getFirst(MCP_SESSION_HEADER);

        if (status.value() == 401 || status.value() == 403) {
            throw new McpConnectionException(
                    "Invalid credentials — check email and API token",
                    "MCP auth failed with status " + status.value());
        }

        if (status.is4xxClientError() || status.is5xxServerError()) {
            throw new McpConnectionException(
                    "Could not reach MCP server — try again later",
                    "MCP request failed method=" + method + " status=" + status.value()
                            + " body=" + truncate(responseBody));
        }

        JsonNode result = parseJsonRpcResult(responseBody, method);

        return new McpHttpResponse(sessionId, result);
    }

    private JsonNode parseJsonRpcResult(String responseBody, String method) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new McpDiscoveryException(
                    "Could not reach MCP server — try again later",
                    "Empty response for method " + method);
        }

        try {
            String jsonPayload = extractJsonPayload(responseBody);
            JsonNode root = OBJECT_MAPPER.readTree(jsonPayload);

            if (root.has("error")) {
                String errorMessage = root.path("error").path("message").asText("Unknown MCP error");
                throw new McpDiscoveryException(
                        "Could not reach MCP server — try again later",
                        "MCP JSON-RPC error for " + method + ": " + errorMessage);
            }

            return root.path("result");
        } catch (McpDiscoveryException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new McpDiscoveryException(
                    "Could not reach MCP server — try again later",
                    "Failed to parse MCP response for " + method + ": " + exception.getMessage());
        }
    }

    private String extractJsonPayload(String responseBody) {
        if (responseBody.trim().startsWith("{")) {
            return responseBody;
        }
        for (String line : responseBody.split("\n")) {
            if (line.startsWith("data:")) {
                String data = line.substring("data:".length()).trim();
                if (data.startsWith("{")) {
                    return data;
                }
            }
        }
        return responseBody;
    }

    private void applyHeaders(
            HttpHeaders headers,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion
    ) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(
                MediaType.APPLICATION_JSON,
                MediaType.TEXT_EVENT_STREAM
        ));
        if (authHeaders != null) {
            authHeaders.forEach(headers::set);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            headers.set(MCP_SESSION_HEADER, sessionId);
        }
        if (protocolVersion != null && !protocolVersion.isBlank()) {
            headers.set(MCP_PROTOCOL_VERSION_HEADER, protocolVersion);
        }
    }

    private String toJson(ObjectNode body) {
        try {
            return OBJECT_MAPPER.writeValueAsString(body);
        } catch (Exception exception) {
            throw new McpConnectionException(
                    "Could not reach MCP server — try again later",
                    "Failed to serialize MCP request: " + exception.getMessage());
        }
    }

    private static String truncate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }

    private static boolean isRateLimitException(McpConnectionException exception) {
        String detail = exception.getMessage();
        return detail != null && detail.contains("HTTP 429");
    }

    private static long retryAfterMillis(HttpHeaders headers) {
        if (headers == null) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        String retryAfter = headers.getFirst("Retry-After");
        return retryAfterMillisFromHeader(retryAfter);
    }

    private static long retryAfterMillisFromDetail(String detail) {
        if (detail == null) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        int marker = detail.indexOf("retryAfterMs=");
        if (marker >= 0) {
            String value = detail.substring(marker + "retryAfterMs=".length()).trim();
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return DEFAULT_RETRY_AFTER_MS;
            }
        }
        return DEFAULT_RETRY_AFTER_MS;
    }

    private static long retryAfterMillisFromHeader(String retryAfter) {
        if (retryAfter == null || retryAfter.isBlank()) {
            return DEFAULT_RETRY_AFTER_MS;
        }
        try {
            long seconds = Long.parseLong(retryAfter.trim());
            return Math.max(0, seconds) * 1000L;
        } catch (NumberFormatException ignored) {
            return DEFAULT_RETRY_AFTER_MS;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    public record McpSession(String sessionId, String protocolVersion) {}

    private record McpHttpResponse(String sessionId, JsonNode result) {}
}
