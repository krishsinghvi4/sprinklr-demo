package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Top-level router response for non-streaming chat completion. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(
        List<ChatCompletionChoice> choices,
        UsageDto usage,
        Double spending,
        AdditionalDto additional
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdditionalDto(
            SpendingDto spending
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SpendingDto(
            Double total
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UsageDto(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens
    ) {
    }

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
