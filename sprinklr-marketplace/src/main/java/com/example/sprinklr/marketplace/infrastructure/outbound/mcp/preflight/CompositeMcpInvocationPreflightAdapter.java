package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs all registered preflight strategies; first block wins.
 */
@Component
public class CompositeMcpInvocationPreflightAdapter implements McpInvocationPreflightPort {

    private final List<McpInvocationPreflightStrategy> strategies;

    public CompositeMcpInvocationPreflightAdapter(List<McpInvocationPreflightStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        for (McpInvocationPreflightStrategy strategy : strategies) {
            PreflightResult result = strategy.validate(invocation, userPrompt);
            if (!result.allowed()) {
                return result;
            }
        }
        return PreflightResult.allow();
    }
}
