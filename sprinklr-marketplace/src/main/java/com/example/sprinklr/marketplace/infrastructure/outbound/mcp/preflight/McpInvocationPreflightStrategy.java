package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;

/**
 * Provider-specific preflight guard. Composite adapter delegates to all registered strategies.
 */
public interface McpInvocationPreflightStrategy {

    PreflightResult validate(McpInvocation invocation, String userPrompt);
}
