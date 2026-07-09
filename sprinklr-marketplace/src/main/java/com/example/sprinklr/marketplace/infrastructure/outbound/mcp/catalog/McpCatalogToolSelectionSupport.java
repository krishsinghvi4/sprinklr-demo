package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Resolves catalog-driven tool-selection overrides for connected MCP servers.
 */
@Component
public class McpCatalogToolSelectionSupport {

    private final McpCatalogLoader catalogLoader;

    public McpCatalogToolSelectionSupport(McpCatalogLoader catalogLoader) {
        this.catalogLoader = catalogLoader;
    }

    public Set<String> continuationNeverSatisfyToolsForToolNames(Iterable<String> toolNames) {
        return continuationNeverSatisfyToolsForPrefixes(prefixesOf(toolNames));
    }

    public Set<String> continuationNeverSatisfyToolsForPrefixes(Set<String> serverIdPrefixes) {
        Set<String> neverSatisfy = new HashSet<>();
        for (McpCatalogEntry entry : catalogLoader.getAll()) {
            if (!serverIdPrefixes.contains(entry.serverIdPrefix())) {
                continue;
            }
            if (entry.toolSelection() != null) {
                neverSatisfy.addAll(entry.toolSelection().continuationNeverSatisfyTools());
            }
        }
        return neverSatisfy;
    }

    private Set<String> prefixesOf(Iterable<String> toolNames) {
        Set<String> prefixes = new LinkedHashSet<>();
        for (String name : toolNames) {
            int dot = name.indexOf('.');
            prefixes.add(dot > 0 ? name.substring(0, dot) : name);
        }
        return prefixes;
    }
}
