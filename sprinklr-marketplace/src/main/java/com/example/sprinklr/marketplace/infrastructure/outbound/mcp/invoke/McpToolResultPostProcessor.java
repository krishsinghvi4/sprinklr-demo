package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;

/**
 * Optional per-server tool result post-processing after a successful MCP tools/call.
 */
public interface McpToolResultPostProcessor {

    boolean supports(McpCatalogEntry entry, String toolName);

    String process(McpCatalogEntry entry, String toolName, String rawContent, McpInvocationContext context);
}
