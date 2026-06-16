package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class McpAuthStrategyRegistry {

    private final Map<String, McpAuthStrategy> strategiesByType;

    public McpAuthStrategyRegistry(List<McpAuthStrategy> strategies) {
        this.strategiesByType = strategies.stream()
                .collect(Collectors.toMap(McpAuthStrategy::authType, Function.identity()));
    }

    public McpAuthStrategy require(String authType) {
        McpAuthStrategy strategy = strategiesByType.get(authType);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported MCP auth type: " + authType);
        }
        return strategy;
    }
}
