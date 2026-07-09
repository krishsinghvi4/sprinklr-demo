package com.example.sprinklr.marketplace.domain.model.MCP;

import java.time.Instant;
import java.util.List;

/**
 * Per-user MCP server configuration added via the profile UI.
 */
public record UserMcpConfig(
        String id,
        String userId,
        String displayName,
        String description,
        String endpointUrl,
        String serverIdPrefix,
        String tokenField,
        McpCredentialHeaderMode headerMode,
        String customHeaderName,
        List<McpCredentialField> credentialFields,
        McpConnectProbeConfig connectProbe,
        String skillText,
        Instant createdAt
) {

    public UserMcpConfig {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig id must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig userId must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig displayName must not be blank");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig endpointUrl must not be blank");
        }
        if (serverIdPrefix == null || serverIdPrefix.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig serverIdPrefix must not be blank");
        }
        if (tokenField == null || tokenField.isBlank()) {
            throw new IllegalArgumentException("UserMcpConfig tokenField must not be blank");
        }
        if (headerMode == null) {
            throw new IllegalArgumentException("UserMcpConfig headerMode must not be null");
        }
        if (headerMode == McpCredentialHeaderMode.CUSTOM
                && (customHeaderName == null || customHeaderName.isBlank())) {
            throw new IllegalArgumentException("UserMcpConfig customHeaderName is required when headerMode is CUSTOM");
        }
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
