package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions;

public class McpCircuitOpenException extends RuntimeException {

    private final String userMessage;

    public McpCircuitOpenException(String userMessage) {
        super(userMessage);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
