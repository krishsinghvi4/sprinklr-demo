package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.MergedCatalogResolver;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpConnectionDocument;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Merges catalog-defined local tool extensions into a connection's discovered remote tool list.
 */
@Component
public class McpLocalToolCatalogMerger {

    private final MergedCatalogResolver catalogResolver;
    private final CompositeMcpLocalToolExtension localToolExtension;

    public McpLocalToolCatalogMerger(
            MergedCatalogResolver catalogResolver,
            CompositeMcpLocalToolExtension localToolExtension
    ) {
        this.catalogResolver = catalogResolver;
        this.localToolExtension = localToolExtension;
    }

    public List<McpTool> merge(McpConnectionDocument connection, List<McpTool> remoteTools) {
        return catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .map(entry -> merge(entry, connection.id(), remoteTools))
                .orElse(remoteTools);
    }

    public List<McpTool> merge(McpCatalogEntry entry, String connectionId, List<McpTool> remoteTools) {
        List<McpTool> localTools = localToolExtension.toolDefinitions(entry, connectionId);
        if (localTools.isEmpty()) {
            return remoteTools;
        }

        LinkedHashMap<String, McpTool> byName = new LinkedHashMap<>();
        for (McpTool tool : remoteTools) {
            byName.put(tool.name(), tool);
        }
        for (McpTool localTool : localTools) {
            byName.putIfAbsent(localTool.name(), localTool);
        }
        return List.copyOf(byName.values());
    }

    public Optional<McpCatalogEntry> catalogEntryFor(McpConnectionDocument connection) {
        return catalogResolver.findById(connection.catalogServerId(), connection.userId());
    }
}
