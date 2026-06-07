package com.example.sprinklr.marketplace.domain.model;

public record ToolResult(String toolCallId, String content) {

    public ToolResult {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("ToolResult toolCallId must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("ToolResult content must not be null");
        }
    }
}
