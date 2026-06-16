package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Request body for Sprinklr IntuitionX {@code /chat-completion}.
 * Field names match the router's OpenAI-compatible JSON contract (snake_case).
 */
//backend must send in this format to the LLM ,Non null so that the values that arent speicified just dont go to the LLM to save bloat
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        @JsonProperty("client_identifier") String clientIdentifier,
        @JsonProperty("partner_id") int partnerId,
        List<LlmApiMessage> messages,
        String provider,
        List<LlmApiTool> tools,
        @JsonProperty("tool_choice") String toolChoice,
        double temperature,
        @JsonProperty("max_completion_tokens") int maxCompletionTokens,
        @JsonProperty("tracking_params") Map<String, String> trackingParams
) {
}
