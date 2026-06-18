package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;

import java.util.Map;

/**
 * Base implementation shared by all MCP providers.
 * Delegates header construction to the auth strategy registry keyed by catalog authType.
 */
public abstract class AbstractMcpProvider implements McpProvider {

    protected final McpAuthStrategyRegistry authStrategyRegistry;

    protected AbstractMcpProvider(McpAuthStrategyRegistry authStrategyRegistry) {
        this.authStrategyRegistry = authStrategyRegistry;
    }

    @Override
    public McpConnectMethod connectMethod(McpCatalogEntry entry) {
        return entry.connectMethod();
    }

    @Override
    public String providerKey(McpCatalogEntry entry) {
        if (entry.authConfig().isOAuth()) {
            return entry.authConfig().oauth().providerKey();
        }
        return entry.id();
    }

    @Override
    public void validateCredentials(McpCatalogEntry entry, Map<String, String> credentials) {
        entry.credentialFields().forEach(field -> {
            if (field.required()) {
                String value = credentials.get(field.key());
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException(field.label() + " is required");
                }
            }
        });
    }

    @Override
    public Map<String, String> buildAuthHeaders(McpCatalogEntry entry, Map<String, String> credentials) {
        return authStrategyRegistry.require(entry.authType()).buildAuthHeaders(credentials);
    }
}
