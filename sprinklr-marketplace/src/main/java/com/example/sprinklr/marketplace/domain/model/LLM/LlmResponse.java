package com.example.sprinklr.marketplace.domain.model.LLM;

import com.example.sprinklr.marketplace.domain.model.tool.ToolCall;

import java.util.List;

public record LlmResponse(
        String content,
        List<ToolCall> toolCalls
) {

    public LlmResponse {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);

        boolean hasContent = content != null && !content.isBlank();
        boolean hasToolCalls = !toolCalls.isEmpty();

        if (!hasContent && !hasToolCalls) {
            throw new IllegalArgumentException(
                    "LlmResponse must have at least one of content or toolCalls");
        }
    }
}
