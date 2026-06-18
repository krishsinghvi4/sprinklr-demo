package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves effective OAuth settings for a catalog entry, falling back to global McpProperties
 * for legacy catalog entries that only specify authType.
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
                catalogOAuth.metadataUrl(),
                resolveResource(catalogOAuth),
                resolveScopes(catalogOAuth),
                catalogOAuth.useDcr(),
                catalogOAuth.usePkce(),
                catalogOAuth.includeResourceParam(),
                catalogOAuth.requiresRefreshToken()
        );
    }

    public String redirectUri() {
        return properties.getOauthRedirectUri();
    }

    private String resolveResource(McpOAuthCatalogConfig catalogOAuth) {
        if (catalogOAuth.resource() != null && !catalogOAuth.resource().isBlank()) {
            return catalogOAuth.resource();
        }
        return properties.getOauthResource();
    }

    private String resolveScopes(McpOAuthCatalogConfig catalogOAuth) {
        if (catalogOAuth.scopes() != null && !catalogOAuth.scopes().isBlank()) {
            return catalogOAuth.scopes();
        }
        return properties.getOauthScopes();
    }
}
