package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;

import java.util.Map;

/**
 * Context passed to local tool handlers for credential access and argument parsing.
 */
public record McpLocalToolInvocationContext(
        McpCatalogEntry catalogEntry,
        McpConnectionDocument connection,
        Map<String, String> credentials,
        Map<String, String> authHeaders,
        StreamableHttpMcpClient.McpSession session,
        String argumentsJson
) {
}
