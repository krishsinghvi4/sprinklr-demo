package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

/**
 * @deprecated Use {@link McpOAuthToken} — retained for backward compatibility during migration.
 */
@Deprecated
public final class AtlassianOAuthToken {

    public static final String ACCESS_TOKEN_KEY = McpOAuthToken.ACCESS_TOKEN_KEY;
    public static final String REFRESH_TOKEN_KEY = McpOAuthToken.REFRESH_TOKEN_KEY;
    public static final String EXPIRES_AT_KEY = McpOAuthToken.EXPIRES_AT_KEY;
    public static final String SCOPE_KEY = McpOAuthToken.SCOPE_KEY;
    public static final String TOKEN_TYPE_KEY = McpOAuthToken.TOKEN_TYPE_KEY;

    private AtlassianOAuthToken() {
    }

    public static McpOAuthToken fromCredentialMap(java.util.Map<String, String> map) {
        return McpOAuthToken.fromCredentialMap(map);
    }
}
