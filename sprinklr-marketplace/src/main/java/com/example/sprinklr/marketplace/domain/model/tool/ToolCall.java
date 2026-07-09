package com.example.sprinklr.marketplace.domain.model.tool;

public record ToolCall(String id, String name, String argumentsJson) {

    public ToolCall {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ToolCall id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("ToolCall name must not be blank");
        }
        if (argumentsJson == null) {
            throw new IllegalArgumentException("ToolCall argumentsJson must not be null");
        }
    }
}
