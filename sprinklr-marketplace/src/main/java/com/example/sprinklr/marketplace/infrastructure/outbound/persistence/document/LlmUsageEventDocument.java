package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

@Document(collection = "llm_usage_events")
@CompoundIndex(name = "user_created_at", def = "{'userId': 1, 'createdAt': -1}")
public record LlmUsageEventDocument(
        @Id String id,
        String userId,
        String conversationId,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal estimatedCostUsd,
        Instant createdAt
) {
}
