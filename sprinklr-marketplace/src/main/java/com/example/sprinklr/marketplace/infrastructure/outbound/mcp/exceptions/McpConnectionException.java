package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

public class McpConnectionException extends RuntimeException {

    private final String userMessage;

    public McpConnectionException(String userMessage, String detail) {
        super(detail);
        this.userMessage = userMessage;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
