package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import org.springframework.stereotype.Component;

/**
 * Atlassian-specific provider for Jira MCP.
 * Handles OAuth quirks (DCR, PKCE, RFC 8707 resource param) while reusing generic connect logic.
 */
@Component
public class AtlassianMcpProvider extends AbstractMcpProvider {

    public static final String PROVIDER_KEY = "atlassian";

    public AtlassianMcpProvider(McpAuthStrategyRegistry authStrategyRegistry) {
        super(authStrategyRegistry);
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return AtlassianOAuthAuthStrategy.AUTH_TYPE.equals(entry.authType())
                || (entry.authConfig().isOAuth()
                && PROVIDER_KEY.equals(entry.authConfig().oauth().providerKey()));
    }

    @Override
    public String providerKey(McpCatalogEntry entry) {
        return PROVIDER_KEY;
    }
}
