package com.example.sprinklr.marketplace.domain.model.LLM;

public record LlmUsageSummary(
        LlmUsagePeriodSummary allTime,
        LlmUsagePeriodSummary currentMonth
) {
}
