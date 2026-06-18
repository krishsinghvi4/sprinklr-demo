package com.example.sprinklr.marketplace.domain.model;

/**
 * OAuth configuration for a catalog MCP server.
 * Values are loaded from catalog JSON; global {@code McpProperties} act as fallback for legacy entries.
 */
public record McpOAuthCatalogConfig(
        String providerKey,
        String metadataUrl,
        String resource,
        String scopes,
        boolean useDcr,
        boolean usePkce,
        boolean includeResourceParam,
        boolean requiresRefreshToken
) {

    public McpOAuthCatalogConfig {
        if (providerKey == null || providerKey.isBlank()) {
            throw new IllegalArgumentException("McpOAuthCatalogConfig providerKey must not be blank");
        }
        if (metadataUrl == null || metadataUrl.isBlank()) {
            throw new IllegalArgumentException("McpOAuthCatalogConfig metadataUrl must not be blank");
        }
        scopes = scopes == null ? "" : scopes;
    }

    /** Stable key used for DCR client lookup and OAuth state scoping. */
    public String providerKey() {
        return providerKey;
    }
}
