package com.example.sprinklr.marketplace.domain.model.MCP;

public record McpInvocationResult(
        String toolCallId,
        boolean success,
        String content,
        String errorMessage
) {

    public McpInvocationResult {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("McpInvocationResult toolCallId must not be blank");
        }
        if (success && content == null) {
            throw new IllegalArgumentException("McpInvocationResult content must not be null on success");
        }
        if (!success && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("McpInvocationResult errorMessage must not be blank on failure");
        }
    }
}
