package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

public record McpOAuthMetadata(
        String authorizationEndpoint,
        String tokenEndpoint,
        String registrationEndpoint
) {}
