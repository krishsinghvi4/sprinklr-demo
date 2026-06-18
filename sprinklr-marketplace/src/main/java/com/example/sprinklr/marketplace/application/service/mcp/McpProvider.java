package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;

import java.util.Map;

/**
 * Central contract for MCP marketplace providers.
 * Each catalog entry is served by a provider that knows how to authenticate and connect.
 */
public interface McpProvider {

    /** Returns true when this provider handles the given catalog entry. */
    boolean supports(McpCatalogEntry entry);

    /** Stable provider key used for OAuth DCR/state scoping (defaults to catalog id). */
    String providerKey(McpCatalogEntry entry);

    McpConnectMethod connectMethod(McpCatalogEntry entry);

    /** Validates user-supplied credentials against catalog field definitions. */
    void validateCredentials(McpCatalogEntry entry, Map<String, String> credentials);

    /** Builds HTTP auth headers for MCP discovery and tool invocation. */
    Map<String, String> buildAuthHeaders(McpCatalogEntry entry, Map<String, String> credentials);

    /** Human-readable display name for error messages. */
    default String displayName(McpCatalogEntry entry) {
        return entry.displayName();
    }
}
