package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic OAuth token model stored encrypted in mcp_connections.
 * Credential map keys are stable across OAuth providers for refresh compatibility.
 */
public record McpOAuthToken(
        String accessToken,
        String refreshToken,
        long expiresAtEpochSec,
        String scope,
        String tokenType
) {
    public static final String ACCESS_TOKEN_KEY = "accessToken";
    public static final String REFRESH_TOKEN_KEY = "refreshToken";
    public static final String EXPIRES_AT_KEY = "expiresAtEpochSec";
    public static final String SCOPE_KEY = "scopes";
    public static final String TOKEN_TYPE_KEY = "tokenType";

    public Map<String, String> toCredentialMap() {
        Map<String, String> map = new HashMap<>();
        map.put(ACCESS_TOKEN_KEY, accessToken);
        map.put(REFRESH_TOKEN_KEY, refreshToken);
        map.put(EXPIRES_AT_KEY, Long.toString(expiresAtEpochSec));
        if (scope != null) {
            map.put(SCOPE_KEY, scope);
        }
        if (tokenType != null) {
            map.put(TOKEN_TYPE_KEY, tokenType);
        }
        return map;
    }

    public boolean isExpired(Instant now) {
        return expiresAtEpochSec <= now.getEpochSecond();
    }

    public static McpOAuthToken fromCredentialMap(Map<String, String> map) {
        if (map == null) {
            return null;
        }
        String accessToken = map.get(ACCESS_TOKEN_KEY);
        String refreshToken = map.get(REFRESH_TOKEN_KEY);
        String expiresAtRaw = map.get(EXPIRES_AT_KEY);
        long expiresAt = 0;
        if (expiresAtRaw != null) {
            try {
                expiresAt = Long.parseLong(expiresAtRaw);
            } catch (NumberFormatException ignored) {
                expiresAt = 0;
            }
        }
        return new McpOAuthToken(
                accessToken,
                refreshToken,
                expiresAt,
                map.get(SCOPE_KEY),
                map.get(TOKEN_TYPE_KEY)
        );
    }
}
