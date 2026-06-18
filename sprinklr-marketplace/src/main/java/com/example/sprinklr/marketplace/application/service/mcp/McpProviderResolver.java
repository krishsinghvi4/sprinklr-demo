package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves the appropriate {@link McpProvider} for a catalog entry.
 * Specialized providers (e.g. Atlassian) are checked before the default catalog provider.
 */
@Component
public class McpProviderResolver {

    private final List<McpProvider> specializedProviders;
    private final DefaultCatalogMcpProvider defaultProvider;
    private final McpCatalogLoader catalogLoader;

    public McpProviderResolver(
            List<McpProvider> allProviders,
            DefaultCatalogMcpProvider defaultProvider,
            McpCatalogLoader catalogLoader
    ) {
        this.defaultProvider = defaultProvider;
        this.catalogLoader = catalogLoader;
        // Exclude default from specialized list so it is always the fallback.
        this.specializedProviders = allProviders.stream()
                .filter(provider -> !(provider instanceof DefaultCatalogMcpProvider))
                .toList();
    }

    public McpProvider require(String catalogServerId) {
        McpCatalogEntry entry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));
        return resolve(entry);
    }

    public McpProvider resolve(McpCatalogEntry entry) {
        return specializedProviders.stream()
                .filter(provider -> provider.supports(entry))
                .findFirst()
                .orElse(defaultProvider);
    }
}
