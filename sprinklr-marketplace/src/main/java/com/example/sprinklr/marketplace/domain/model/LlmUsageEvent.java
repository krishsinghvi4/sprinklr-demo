package com.example.sprinklr.marketplace.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record LlmUsageEvent(
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
