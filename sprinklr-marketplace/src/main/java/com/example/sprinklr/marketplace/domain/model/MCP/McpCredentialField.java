package com.example.sprinklr.marketplace.domain.model.MCP;

public record McpCredentialField(
        String key,
        String label,
        String type,
        boolean required
) {

    public McpCredentialField {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("McpCredentialField key must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("McpCredentialField label must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("McpCredentialField type must not be blank");
        }
    }
}
