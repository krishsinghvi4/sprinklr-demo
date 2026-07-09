package com.example.sprinklr.marketplace.domain.model.MCP;

/**
 * How credential-based MCP servers expect the access token on outbound HTTP headers.
 */
public enum McpCredentialHeaderMode {
    BEARER,
    PRIVATE_TOKEN,
    BASIC_EMAIL,
    CUSTOM
}
