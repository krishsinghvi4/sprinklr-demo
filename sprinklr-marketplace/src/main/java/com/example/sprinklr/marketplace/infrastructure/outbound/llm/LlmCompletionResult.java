package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.ToolCall;

import java.util.List;

/** Parsed outcome of a single non-streaming router call, before mapping to domain {@link com.example.sprinklr.marketplace.domain.model.LlmResponse}. */
public record LlmCompletionResult(
        String content,
        List<ToolCall> toolCalls
) {
}
