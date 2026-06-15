package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Top-level router response for non-streaming chat completion. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        List<ChatCompletionChoice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionChoice(
            Integer index,
            ChatCompletionMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatCompletionMessage(
            String role,
            String content,
            @JsonProperty("tool_calls") List<LlmApiToolCall> toolCalls
    ) {
    }
}
