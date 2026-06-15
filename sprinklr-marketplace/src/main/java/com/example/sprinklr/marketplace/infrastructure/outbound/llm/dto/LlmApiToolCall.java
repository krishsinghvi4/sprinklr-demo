package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Tool call emitted by the assistant in router responses and replayed in conversation history.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmApiToolCall(
        String id,
        String type,
        LlmApiToolCallFunction function
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmApiToolCallFunction(
            String name,
            String arguments
    ) {
    }
}
