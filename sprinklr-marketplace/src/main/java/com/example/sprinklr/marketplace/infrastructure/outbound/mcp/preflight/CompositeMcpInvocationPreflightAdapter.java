package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs all registered preflight strategies; first block wins.
 */
@Component
public class CompositeMcpInvocationPreflightAdapter implements McpInvocationPreflightPort {

    private static final Logger log = LoggerFactory.getLogger(CompositeMcpInvocationPreflightAdapter.class);

    private final List<McpInvocationPreflightStrategy> strategies;

    public CompositeMcpInvocationPreflightAdapter(List<McpInvocationPreflightStrategy> strategies) {
        this.strategies = strategies;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        log.info("[Preflight] Starting validation tool={} connectionId={} strategyCount={}",
                invocation.toolName(), invocation.serverId(), strategies.size());

        for (McpInvocationPreflightStrategy strategy : strategies) {
            String strategyName = strategy.getClass().getSimpleName();
            PreflightResult result = strategy.validate(invocation, userPrompt);
            if (!result.allowed()) {
                log.info("[Preflight] BLOCKED tool={} strategy={} reason={}",
                        invocation.toolName(), strategyName, truncate(result.blockMessage()));
                return result;
            }
            log.debug("[Preflight] Passed strategy={} tool={}", strategyName, invocation.toolName());
        }

        log.info("[Preflight] ALLOWED tool={} connectionId={}", invocation.toolName(), invocation.serverId());
        return PreflightResult.allow();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= 200 ? value : value.substring(0, 200) + "...";
    }
}
