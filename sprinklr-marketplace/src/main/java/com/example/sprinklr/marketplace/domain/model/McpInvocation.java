package com.example.sprinklr.marketplace.domain.model;

public record McpInvocation(
        String serverId,
        String toolName,
        String argumentsJson
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
    }
}
