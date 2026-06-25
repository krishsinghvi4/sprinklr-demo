package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions;

public class McpOAuthException extends RuntimeException {
    private final String userMessage;

    public McpOAuthException(String userMessage, String logMessage) {
        super(logMessage);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
