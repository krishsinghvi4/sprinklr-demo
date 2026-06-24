package com.example.sprinklr.marketplace.domain.model;

public record LlmUsageSummary(
        LlmUsagePeriodSummary allTime,
        LlmUsagePeriodSummary currentMonth
) {
}
