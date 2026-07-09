package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;

/**
 * Optional per-server tool argument normalization before MCP tools/call.
 */
public interface McpToolArgumentNormalizer {

    boolean supports(McpCatalogEntry entry, String toolName);

    String normalize(McpCatalogEntry entry, String toolName, String argumentsJson, String connectionId);
}
