package com.example.sprinklr.marketplace.domain.model.MCP;

/**
 * Authentication configuration bundled with a catalog entry.
 */
public record McpAuthConfig(
        McpAuthKind kind,
        McpOAuthCatalogConfig oauth,
        McpCredentialAuthConfig credentials
) {

    public McpAuthConfig {
        if (kind == null) {
            throw new IllegalArgumentException("McpAuthConfig kind must not be null");
        }
        if (kind == McpAuthKind.OAUTH && oauth == null) {
            throw new IllegalArgumentException("OAuth catalog entries require oauth configuration");
        }
        if (kind == McpAuthKind.CREDENTIALS && credentials == null) {
            throw new IllegalArgumentException("Credential catalog entries require credentials configuration");
        }
    }

    /** OAuth-only constructor for backward compatibility in tests. */
    public McpAuthConfig(McpAuthKind kind, McpOAuthCatalogConfig oauth) {
        this(kind, oauth, null);
    }

    public boolean isOAuth() {
        return kind == McpAuthKind.OAUTH;
    }

    public boolean isCredentials() {
        return kind == McpAuthKind.CREDENTIALS;
    }
}
