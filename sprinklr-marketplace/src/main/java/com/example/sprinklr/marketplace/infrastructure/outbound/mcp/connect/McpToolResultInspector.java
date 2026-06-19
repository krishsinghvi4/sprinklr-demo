package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

final class McpToolResultInspector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private McpToolResultInspector() {
    }

    static Optional<String> extractError(JsonNode result) {
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
        if (looksLikeGitLabAuthFailure(content)) {
            return Optional.of(content);
        }
        return Optional.empty();
    }

    private static boolean looksLikeGitLabAuthFailure(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("401 unauthorized")
                || lower.contains("invalid token")
                || lower.contains("invalid credentials")
                || lower.contains("authentication failed");
    }

    private static String extractErrorMessageFromContent(JsonNode result) {
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
        return result.toString();
    }
}
