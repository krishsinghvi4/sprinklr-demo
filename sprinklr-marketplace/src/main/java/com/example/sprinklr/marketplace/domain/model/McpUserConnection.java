package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.List;

public record McpUserConnection(
        String id,
        String userId,
        String catalogServerId,
        McpConnectionStatus status,
        String mcpSessionId,
        String mcpProtocolVersion,
        List<McpTool> tools,
        Instant connectedAt,
        String lastError
) {

    public McpUserConnection {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("McpUserConnection id must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("McpUserConnection userId must not be blank");
        }
        if (catalogServerId == null || catalogServerId.isBlank()) {
            throw new IllegalArgumentException("McpUserConnection catalogServerId must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("McpUserConnection status must not be null");
        }
        if (connectedAt == null) {
            throw new IllegalArgumentException("McpUserConnection connectedAt must not be null");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
