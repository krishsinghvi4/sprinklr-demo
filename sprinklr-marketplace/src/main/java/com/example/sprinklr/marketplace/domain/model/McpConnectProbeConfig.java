package com.example.sprinklr.marketplace.domain.model;

/**
 * Optional connect-time probe that validates credentials via a lightweight MCP tool call.
 */
public record McpConnectProbeConfig(
        String tool,
        String argumentsJson,
        String failureMessage
) {

    public McpConnectProbeConfig {
        if (tool == null || tool.isBlank()) {
            throw new IllegalArgumentException("McpConnectProbeConfig tool must not be blank");
        }
        if (argumentsJson == null || argumentsJson.isBlank()) {
            argumentsJson = "{}";
        }
        if (failureMessage == null || failureMessage.isBlank()) {
            throw new IllegalArgumentException("McpConnectProbeConfig failureMessage must not be blank");
        }
    }
}
