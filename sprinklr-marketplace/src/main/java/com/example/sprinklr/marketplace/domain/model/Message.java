package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Message(
        String id,
        String conversationId,
        MessageRole role,
        String content,
        List<ToolCall> toolCalls,
        List<ToolResult> toolResults,
        Instant createdAt
) {

    public Message {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Message id must not be blank");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("Message conversationId must not be blank");
        }
        Objects.requireNonNull(role, "Message role must not be null");
        Objects.requireNonNull(createdAt, "Message createdAt must not be null");

        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);

        boolean hasContent = content != null && !content.isBlank();
        boolean hasToolCalls = !toolCalls.isEmpty();
        boolean hasToolResults = !toolResults.isEmpty();

        if (!hasContent && !hasToolCalls && !hasToolResults) {
            throw new IllegalArgumentException(
                    "Message must have at least one of content, toolCalls, or toolResults");
        }
    }
}
