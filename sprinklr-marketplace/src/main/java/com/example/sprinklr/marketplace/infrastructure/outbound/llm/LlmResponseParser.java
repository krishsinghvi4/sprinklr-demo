package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LlmTokenUsage;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.ChatCompletionResponse;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiToolCall;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Deserializes raw router JSON into {@link LlmCompletionResult}.
 * Separated from {@link ChatCompletionClient} so parsing logic can be unit-tested without HTTP.
 */
/**
 * Parses the LLM router response into a minimal domain result.
 * Handles schema drift and validates required fields.
 */
@Component
public class LlmResponseParser {

    /**
     * Router responses follow the OpenAI chat-completion schema and include fields we do not model
     * (e.g. {@code index}, {@code finish_reason}, {@code usage}). Ignore extras so API evolution
     * does not break parsing.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public LlmResponseParser() {
    }

    /**
     * Parses the raw JSON response into content + tool calls.
     */
    public LlmCompletionResult parse(String rawBody) {
        try {
            ChatCompletionResponse response = OBJECT_MAPPER.readValue(rawBody, ChatCompletionResponse.class);
            if (response.choices() == null || response.choices().isEmpty()) {
                throw new LlmClientException("LLM router returned no choices");
            }

            ChatCompletionResponse.ChatCompletionMessage message = response.choices().get(0).message();
            if (message == null) {
                throw new LlmClientException("LLM router returned empty message");
            }

            String content = message.content();
            List<ToolCall> toolCalls = mapToolCalls(message.toolCalls());

            boolean hasContent = content != null && !content.isBlank();
            boolean hasToolCalls = !toolCalls.isEmpty();
            if (!hasContent && !hasToolCalls) {
                throw new LlmClientException("LLM router returned neither content nor tool_calls");
            }

            return new LlmCompletionResult(content, toolCalls, mapUsage(response));
        } catch (LlmClientException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmClientException("Failed to parse LLM router response", e);
        }
    }

    /**
     * Converts API tool call payloads into domain tool calls with safe defaults.
     */
    private LlmTokenUsage mapUsage(ChatCompletionResponse response) {
        ChatCompletionResponse.UsageDto usage = response.usage();
        if (usage == null) {
            return null;
        }
        int prompt = usage.promptTokens() != null ? usage.promptTokens() : 0;
        int completion = usage.completionTokens() != null ? usage.completionTokens() : 0;
        int total = usage.totalTokens() != null ? usage.totalTokens() : prompt + completion;
        if (prompt == 0 && completion == 0 && total == 0) {
            return null;
        }
        return new LlmTokenUsage(prompt, completion, total, resolveSpendingUsd(response));
    }

    private BigDecimal resolveSpendingUsd(ChatCompletionResponse response) {
        Double spending = response.spending();
        if (spending != null && spending > 0) {
            return toUsd(spending);
        }
        ChatCompletionResponse.AdditionalDto additional = response.additional();
        if (additional != null && additional.spending() != null && additional.spending().total() != null) {
            Double nestedTotal = additional.spending().total();
            if (nestedTotal > 0) {
                return toUsd(nestedTotal);
            }
        }
        return null;
    }

    private BigDecimal toUsd(double amount) {
        return BigDecimal.valueOf(amount).setScale(4, RoundingMode.HALF_UP);
    }

    private List<ToolCall> mapToolCalls(List<LlmApiToolCall> apiToolCalls) {
        if (apiToolCalls == null || apiToolCalls.isEmpty()) {
            return List.of();
        }
        return apiToolCalls.stream()
                .map(tc -> new ToolCall(
                        tc.id(),
                        tc.function().name(),
                        tc.function().arguments() != null ? tc.function().arguments() : "{}"
                ))
                .toList();
    }
}
