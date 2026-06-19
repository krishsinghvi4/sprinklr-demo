package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompositeMcpInvocationPreflightAdapterTest {

    @Test
    void returnsFirstBlockFromStrategyChain() {
        McpInvocationPreflightStrategy blocking = (invocation, userPrompt) ->
                PreflightResult.block("blocked by test strategy");
        McpInvocationPreflightStrategy allowing = (invocation, userPrompt) -> PreflightResult.allow();

        McpInvocationPreflightPort port = new CompositeMcpInvocationPreflightAdapter(
                List.of(allowing, blocking)
        );

        var result = port.validate(
                new McpInvocation("conn-1", "createJiraIssue", "{}", "call-1"),
                "create a ticket"
        );
        assertFalse(result.allowed());
    }

    @Test
    void allowsWhenAllStrategiesAllow() {
        McpInvocationPreflightStrategy allowing = (invocation, userPrompt) -> PreflightResult.allow();
        McpInvocationPreflightPort port = new CompositeMcpInvocationPreflightAdapter(List.of(allowing));

        var result = port.validate(
                new McpInvocation("conn-1", "search_issues", "{}", "call-1"),
                "search tickets"
        );
        assertTrue(result.allowed());
    }
}
