package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import java.time.Instant;
import java.util.List;

public record DashboardConversationDetailResponse(
        String id,
        String sourceConversationId,
        String title,
        String preview,
        int turnCount,
        Instant createdAt,
        Instant updatedAt,
        List<DashboardTurnSummaryDto> turns
) {}
