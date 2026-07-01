package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import java.time.Instant;

public record DashboardConversationSummaryDto(
        String id,
        String sourceConversationId,
        String title,
        String preview,
        int turnCount,
        Instant createdAt,
        Instant updatedAt
) {}
