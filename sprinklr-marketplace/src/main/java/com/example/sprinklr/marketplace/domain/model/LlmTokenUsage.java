package com.example.sprinklr.marketplace.domain.model;

import java.math.BigDecimal;

public record LlmTokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        BigDecimal spendingUsd
) {
    public LlmTokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        this(promptTokens, completionTokens, totalTokens, null);
    }

    public LlmTokenUsage {
        if (totalTokens <= 0 && promptTokens <= 0 && completionTokens <= 0) {
            totalTokens = promptTokens + completionTokens;
        }
    }
}
