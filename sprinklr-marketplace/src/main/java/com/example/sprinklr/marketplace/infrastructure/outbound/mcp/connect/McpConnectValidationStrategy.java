package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;

import java.util.Map;

/**
 * Optional live validation after MCP discovery for credential-based servers
 * whose tokens are not verified during initialize/tools/list.
 */
public interface McpConnectValidationStrategy {

    boolean supports(McpCatalogEntry entry);

    void validateLiveConnection(
            McpCatalogEntry entry,
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion
    );
}
