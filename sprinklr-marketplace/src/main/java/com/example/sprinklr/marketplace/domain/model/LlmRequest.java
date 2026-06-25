package com.example.sprinklr.marketplace.domain.model;

import java.util.List;
import java.util.Objects;

public record LlmRequest(
        String prompt,
        List<Message> history,
        List<McpTool> tools,
        String currentTurnUserMessageId,
        String userId,
        String conversationId,
        // Optional extra system context for this turn (e.g. continuation summaries reused across turns).
        String additionalContext
) {

    public LlmRequest(String prompt, List<Message> history, List<McpTool> tools) {
        this(prompt, history, tools, null, null, null, null);
    }

    public LlmRequest(String prompt, List<Message> history, List<McpTool> tools, String currentTurnUserMessageId) {
        this(prompt, history, tools, currentTurnUserMessageId, null, null, null);
    }

    public LlmRequest(
            String prompt,
            List<Message> history,
            List<McpTool> tools,
            String currentTurnUserMessageId,
            String userId,
            String conversationId
    ) {
        this(prompt, history, tools, currentTurnUserMessageId, userId, conversationId, null);
    }

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
