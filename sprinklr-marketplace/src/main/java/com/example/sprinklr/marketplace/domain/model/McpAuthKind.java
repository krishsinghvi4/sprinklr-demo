package com.example.sprinklr.marketplace.domain.model;

/**
 * High-level authentication category for an MCP catalog entry.
 * Specific wire formats are handled by {@code McpAuthStrategy} implementations keyed by authType.
 */
public enum McpAuthKind {
    OAUTH,
    CREDENTIALS
}
