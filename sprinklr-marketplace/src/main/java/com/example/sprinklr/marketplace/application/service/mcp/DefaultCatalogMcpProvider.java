package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.CatalogAuthHeaderBuilder;
import org.springframework.stereotype.Component;

/**
 * Default catalog-driven provider used for all MCP servers.
 * No per-server Java class is required — behavior comes from mcp-catalog.json.
 */
@Component
public class DefaultCatalogMcpProvider extends AbstractMcpProvider {

    public DefaultCatalogMcpProvider(CatalogAuthHeaderBuilder authHeaderBuilder) {
        super(authHeaderBuilder);
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return true;
    }
}
