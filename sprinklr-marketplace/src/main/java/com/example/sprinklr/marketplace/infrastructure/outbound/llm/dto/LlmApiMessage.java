package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single message in the router's {@code messages} array.
 * Supports plain text turns and future MCP tool-call replay (assistant tool_calls + tool results).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmApiMessage(
        String role,
        String content,
        @JsonProperty("tool_calls") List<LlmApiToolCall> toolCalls,
        @JsonProperty("tool_call_id") String toolCallId
) {
}
