package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * Context passed to tool result post-processors for follow-up MCP calls.
 */
public record McpInvocationContext(
        McpCatalogEntry catalogEntry,
        McpConnectionDocument connection,
        Map<String, String> authHeaders,
        StreamableHttpMcpClient.McpSession session,
        String argumentsJson,
        BiFunction<String, String, String> callToolAndFormat
) {
}
