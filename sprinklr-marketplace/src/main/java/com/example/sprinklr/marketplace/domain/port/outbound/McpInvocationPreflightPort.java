package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;

/**
 * Validates MCP tool invocations before they reach the server adapter.
 * Provider-specific guards (e.g. Jira create safety) implement this port.
 */
public interface McpInvocationPreflightPort {

    PreflightResult validate(McpInvocation invocation, String userPrompt);

    record PreflightResult(boolean allowed, String blockMessage) {
        public static PreflightResult allow() {
            return new PreflightResult(true, null);
        }

        public static PreflightResult block(String message) {
            return new PreflightResult(false, message);
        }
    }
}
