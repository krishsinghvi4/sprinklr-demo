package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Validates credentials at connect time using a lightweight probe tool declared in the catalog.
 */
@Component
public class CatalogConnectProbeStrategy implements McpConnectValidationStrategy {

    private static final Logger log = LoggerFactory.getLogger(CatalogConnectProbeStrategy.class);

    private final StreamableHttpMcpClient mcpClient;

    public CatalogConnectProbeStrategy(StreamableHttpMcpClient mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return entry.connectProbe() != null;
    }

    @Override
    public void validateLiveConnection(
            McpCatalogEntry entry,
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion
    ) {
        var probe = entry.connectProbe();
        log.info("[MCP] Validating credentials via probe tool={} catalogServerId={}",
                probe.tool(), entry.id());
        JsonNode result;
        try {
            result = mcpClient.callTool(
                    endpointUrl,
                    authHeaders,
                    sessionId,
                    protocolVersion,
                    probe.tool(),
                    probe.argumentsJson()
            );
        } catch (McpConnectionException exception) {
            throw new McpConnectionException(probe.failureMessage(), exception.getMessage());
        }

        Optional<String> toolError = McpToolResultInspector.extractError(result);
        if (toolError.isPresent()) {
            throw new McpConnectionException(
                    probe.failureMessage(),
                    "Connect probe failed: " + toolError.get()
            );
        }
    }
}
