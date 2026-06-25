package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions;

public class McpDiscoveryException extends RuntimeException {

    private final String userMessage;

    public McpDiscoveryException(String userMessage, String detail) {
        super(detail);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
