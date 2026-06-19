package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CompositeMcpConnectValidationAdapter {

    private final List<McpConnectValidationStrategy> strategies;

    public CompositeMcpConnectValidationAdapter(List<McpConnectValidationStrategy> strategies) {
        this.strategies = strategies;
    }

    public void validateLiveConnection(
            McpCatalogEntry entry,
            String endpointUrl,
            Map<String, String> authHeaders,
            String sessionId,
            String protocolVersion
    ) {
        for (McpConnectValidationStrategy strategy : strategies) {
            if (strategy.supports(entry)) {
                strategy.validateLiveConnection(entry, endpointUrl, authHeaders, sessionId, protocolVersion);
            }
        }
    }
}
