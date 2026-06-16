package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AtlassianOAuthAuthStrategy implements McpAuthStrategy {

    public static final String AUTH_TYPE = "OAUTH_ATLASSIAN";

    @Override
    public String authType() {
        return AUTH_TYPE;
    }

    @Override
    public Map<String, String> buildAuthHeaders(Map<String, String> credentials) {
        String accessToken = credentials.get("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken is required for OAUTH_ATLASSIAN auth");
        }
        return Map.of("Authorization", "Bearer " + accessToken);
    }
}
