package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * Domain representation of an MCP server listed in the marketplace catalog.
 */
public record McpCatalogEntry(
        String id,
        String displayName,
        String description,
        String endpointUrl,
        String serverIdPrefix,
        String authType,
        McpAuthConfig authConfig,
        McpConnectMethod connectMethod,
        List<McpCredentialField> credentialFields
) {

    public McpCatalogEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry displayName must not be blank");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry endpointUrl must not be blank");
        }
        if (serverIdPrefix == null || serverIdPrefix.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry serverIdPrefix must not be blank");
        }
        if (authType == null || authType.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry authType must not be blank");
        }
        if (authConfig == null) {
            throw new IllegalArgumentException("McpCatalogEntry authConfig must not be null");
        }
        if (connectMethod == null) {
            throw new IllegalArgumentException("McpConnectMethod connectMethod must not be null");
        }
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
    }

    /** Returns true when this entry uses OAuth redirect connect flow. */
    public boolean requiresOAuthRedirect() {
        return connectMethod == McpConnectMethod.OAUTH_REDIRECT;
    }
}
