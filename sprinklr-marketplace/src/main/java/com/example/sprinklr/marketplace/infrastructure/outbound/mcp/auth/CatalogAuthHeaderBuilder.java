package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpCredentialAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpCredentialHeaderMode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Builds outbound MCP HTTP auth headers from catalog auth configuration and decrypted credentials.
 */
@Component
public class CatalogAuthHeaderBuilder {

    public Map<String, String> buildHeaders(McpCatalogEntry entry, Map<String, String> credentials) {
        McpAuthConfig authConfig = entry.authConfig();
        if (authConfig.isOAuth()) {
            return oauthBearerHeaders(credentials);
        }
        McpCredentialAuthConfig credentialConfig = authConfig.credentials();
        if (credentialConfig == null) {
            throw new IllegalArgumentException(
                    "Credential catalog entry '" + entry.id() + "' must declare auth.credentials");
        }
        return credentialHeaders(credentialConfig, credentials);
    }

    private Map<String, String> oauthBearerHeaders(Map<String, String> credentials) {
        String accessToken = credentials.get("accessToken");
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken is required for OAuth MCP auth");
        }
        return Map.of("Authorization", "Bearer " + accessToken.trim());
    }

    private Map<String, String> credentialHeaders(
            McpCredentialAuthConfig config,
            Map<String, String> credentials
    ) {
        String token = credentials.get(config.tokenField());
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException(config.tokenField() + " is required for MCP auth");
        }
        token = token.trim();

        return switch (config.headerMode()) {
            case BEARER -> Map.of("Authorization", "Bearer " + token);
            case PRIVATE_TOKEN -> Map.of("Private-Token", token);
            case BASIC_EMAIL -> {
                String email = credentials.get(config.emailField());
                if (email == null || email.isBlank()) {
                    throw new IllegalArgumentException(config.emailField() + " is required for BASIC_EMAIL MCP auth");
                }
                String encoded = Base64.getEncoder()
                        .encodeToString((email.trim() + ":" + token).getBytes(StandardCharsets.UTF_8));
                yield Map.of("Authorization", "Basic " + encoded);
            }
            case CUSTOM -> Map.of(config.customHeaderName(), token);
        };
    }
}
