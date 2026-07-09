package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "messages")
public record MessageDocument(
        @Id String id,
        String conversationId,
        MessageRole role,
        String content,
        List<ToolCallDocument> toolCalls,
        List<ToolResultDocument> toolResults,
        Instant createdAt
) {
    // Nested records map perfectly to embedded JSON arrays in MongoDB
    public record ToolCallDocument(String id, String name, String argumentsJson) {}
    public record ToolResultDocument(String toolCallId, String content) {}
}