package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import org.springframework.stereotype.Component;

/**
 * Default catalog-driven provider used for most MCP servers.
 * No per-server Java class is required — behavior comes from mcp-catalog.json.
 */
@Component
public class DefaultCatalogMcpProvider extends AbstractMcpProvider {

    public DefaultCatalogMcpProvider(McpAuthStrategyRegistry authStrategyRegistry) {
        super(authStrategyRegistry);
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        // Fallback provider — always eligible; specialized providers take precedence.
        return true;
    }
}
