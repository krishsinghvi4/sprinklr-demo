package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import java.util.List;

public record SaveDashboardTurnRequest(
        String sourceConversationId,
        String sourceChatMessageId,
        String prompt,
        String assistantContent,
        List<WidgetSpecDto> widgets,
        String toolResultSnapshot
) {}
