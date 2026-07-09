package com.example.sprinklr.marketplace.domain.model.MCP;

public record McpTool(
        String name,
        String description,
        String serverId,
        String inputSchemaJson
) {

    public McpTool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("McpTool name must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("McpTool description must not be blank");
        }
        if (serverId == null || serverId.isBlank()) {
            throw new IllegalArgumentException("McpTool serverId must not be blank");
        }
        if (inputSchemaJson == null || inputSchemaJson.isBlank()) {
            throw new IllegalArgumentException("McpTool inputSchemaJson must not be blank");
        }
    }
}
