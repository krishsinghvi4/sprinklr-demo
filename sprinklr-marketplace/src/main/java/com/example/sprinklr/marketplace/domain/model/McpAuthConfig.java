package com.example.sprinklr.marketplace.domain.model;

/**
 * Authentication configuration bundled with a catalog entry.
 */
public record McpAuthConfig(
        McpAuthKind kind,
        McpOAuthCatalogConfig oauth
) {

    public McpAuthConfig {
        if (kind == null) {
            throw new IllegalArgumentException("McpAuthConfig kind must not be null");
        }
        if (kind == McpAuthKind.OAUTH && oauth == null) {
            throw new IllegalArgumentException("OAuth catalog entries require oauth configuration");
        }
    }

    public boolean isOAuth() {
        return kind == McpAuthKind.OAUTH;
    }

    public boolean isCredentials() {
        return kind == McpAuthKind.CREDENTIALS;
    }
}
