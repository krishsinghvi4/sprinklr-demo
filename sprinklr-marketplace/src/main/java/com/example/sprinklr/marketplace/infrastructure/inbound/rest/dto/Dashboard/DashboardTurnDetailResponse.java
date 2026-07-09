package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardTurnDetailResponse(
        String id,
        String dashboardConversationId,
        String sourceChatMessageId,
        String prompt,
        String narrative,
        String assistantContent,
        List<WidgetSpecDto> widgets,
        String toolResultSnapshot,
        Map<String, String> extendedInsights,
        int version,
        String previousVersionId,
        Instant createdAt
) {}
