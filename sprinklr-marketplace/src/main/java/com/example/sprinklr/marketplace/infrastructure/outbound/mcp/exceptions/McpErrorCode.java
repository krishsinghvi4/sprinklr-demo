package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions;

/**
 * Machine-readable error codes returned to clients for consistent UI error handling.
 */
public enum McpErrorCode {
    VALIDATION_ERROR,
    AUTH_ERROR,
    OAUTH_ERROR,
    MCP_UNAVAILABLE,
    MCP_CIRCUIT_OPEN,
    NETWORK_ERROR,
    UNKNOWN_ERROR
}
