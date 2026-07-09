package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmTokenUsage;
import com.example.sprinklr.marketplace.domain.model.tool.ToolCall;

import java.util.List;

/** Parsed outcome of a single non-streaming router call, before mapping to domain {@link LlmResponse}. */
public record LlmCompletionResult(
        String content,
        List<ToolCall> toolCalls,
        LlmTokenUsage usage
) {
    public LlmCompletionResult(String content, List<ToolCall> toolCalls) {
        this(content, toolCalls, null);
    }
}
