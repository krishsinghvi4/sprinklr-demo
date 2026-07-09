package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard;

public record SaveDashboardTurnResponse(
        String dashboardConversationId,
        String turnId,
        boolean alreadySaved
) {}
