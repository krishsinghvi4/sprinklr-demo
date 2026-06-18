package com.example.sprinklr.marketplace.domain.model;

/**
 * Describes how the UI should initiate a connection for a catalog MCP server.
 */
public enum McpConnectMethod {
    /** Redirect the user to an OAuth authorization URL (PKCE flow). */
    OAUTH_REDIRECT,
    /** Show a credential form (API token, basic auth, etc.) and POST to /connections. */
    CREDENTIAL_FORM
}
