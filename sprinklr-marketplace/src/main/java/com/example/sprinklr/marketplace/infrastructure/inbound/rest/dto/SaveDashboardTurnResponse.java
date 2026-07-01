package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record SaveDashboardTurnResponse(
        String dashboardConversationId,
        String turnId
) {}
