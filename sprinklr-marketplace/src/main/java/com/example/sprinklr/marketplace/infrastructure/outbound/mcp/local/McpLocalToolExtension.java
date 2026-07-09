package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;

import java.util.List;

/**
 * Optional per-server local tool definitions executed in-process instead of remote MCP tools/call.
 */
public interface McpLocalToolExtension {

    boolean supports(McpCatalogEntry entry);

    List<McpTool> toolDefinitions(McpCatalogEntry entry, String connectionId);

    boolean handles(McpCatalogEntry entry, String bareToolName);

    String invoke(McpCatalogEntry entry, String bareToolName, McpLocalToolInvocationContext context);
}
