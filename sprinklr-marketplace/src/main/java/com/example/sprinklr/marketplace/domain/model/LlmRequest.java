package com.example.sprinklr.marketplace.domain.model;

import java.util.List;
import java.util.Objects;

public record LlmRequest(
        String prompt,
        List<Message> history,
        List<McpTool> tools
) {

    public LlmRequest {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("LlmRequest prompt must not be blank");
        }
        Objects.requireNonNull(history, "LlmRequest history must not be null");
        Objects.requireNonNull(tools, "LlmRequest tools must not be null");
        history = List.copyOf(history);
        tools = List.copyOf(tools);
    }
}
