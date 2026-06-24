package com.example.sprinklr.marketplace.domain.model;

import java.math.BigDecimal;

public record LlmUsagePeriodSummary(
        long totalTokens,
        long promptTokens,
        long completionTokens,
        BigDecimal estimatedCostUsd,
        long llmCallCount
) {
    public static LlmUsagePeriodSummary empty() {
        return new LlmUsagePeriodSummary(0, 0, 0, BigDecimal.ZERO, 0);
    }
}
