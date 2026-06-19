package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves effective OAuth settings for a catalog entry.
 * Per-issuer metadata, resource, and scopes must be declared in the catalog auth.oauth block.
 */
@Component
public class McpOAuthConfigResolver {

    private final McpProperties properties;

    public McpOAuthConfigResolver(McpProperties properties) {
        this.properties = properties;
    }

    public McpOAuthCatalogConfig resolve(McpCatalogEntry entry) {
        if (!entry.authConfig().isOAuth()) {
            throw new IllegalArgumentException("Catalog entry is not OAuth-enabled: " + entry.id());
        }
        McpOAuthCatalogConfig catalogOAuth = entry.authConfig().oauth();
        return new McpOAuthCatalogConfig(
                catalogOAuth.providerKey(),
                requireNonBlank(catalogOAuth.metadataUrl(), "metadataUrl", entry.id()),
                requireNonBlank(catalogOAuth.resource(), "resource", entry.id()),
                requireNonBlank(catalogOAuth.scopes(), "scopes", entry.id()),
                catalogOAuth.useDcr(),
                catalogOAuth.usePkce(),
                catalogOAuth.includeResourceParam(),
                catalogOAuth.requiresRefreshToken()
        );
    }

    public String redirectUri() {
        return properties.getOauthRedirectUri();
    }

    private static String requireNonBlank(String value, String fieldName, String catalogId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "OAuth catalog entry '" + catalogId + "' is missing auth.oauth." + fieldName);
        }
        return value;
    }
}
