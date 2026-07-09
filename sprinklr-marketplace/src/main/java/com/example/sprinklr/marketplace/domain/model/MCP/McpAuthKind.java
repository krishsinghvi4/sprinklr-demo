package com.example.sprinklr.marketplace.domain.model.MCP;

/**
 * High-level authentication mode declared in the MCP catalog.
 * Wire formats are handled by {@code CatalogAuthHeaderBuilder} from catalog auth configuration.
 */
public enum McpAuthKind {
    OAUTH,
    CREDENTIALS
}
