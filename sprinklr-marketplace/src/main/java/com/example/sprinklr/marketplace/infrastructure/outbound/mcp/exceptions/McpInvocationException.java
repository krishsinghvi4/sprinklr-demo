package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions;

public class McpInvocationException extends RuntimeException {

    private final String userMessage;

    public McpInvocationException(String userMessage, String detail) {
        super(detail);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
