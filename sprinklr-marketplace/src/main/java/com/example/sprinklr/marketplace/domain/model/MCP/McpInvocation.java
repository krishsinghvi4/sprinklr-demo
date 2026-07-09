package com.example.sprinklr.marketplace.domain.model.MCP;

public record McpInvocation(
        String serverId,
        String toolName,
        String argumentsJson,
        String toolCallId
) {

    public McpInvocation {
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("McpInvocation serverId must not be blank");
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("McpInvocation toolName must not be blank");
        }
        if (argumentsJson == null) {
            throw new IllegalArgumentException("McpInvocation argumentsJson must not be null");
        }
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("McpInvocation toolCallId must not be blank");
        }
    }

    public McpInvocation(String serverId, String toolName, String argumentsJson) {
        this(serverId, toolName, argumentsJson, java.util.UUID.randomUUID().toString());
    }
}
