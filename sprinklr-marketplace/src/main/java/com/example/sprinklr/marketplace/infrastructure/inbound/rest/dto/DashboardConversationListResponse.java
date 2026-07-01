package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import java.util.List;

public record DashboardConversationListResponse(
        List<DashboardConversationSummaryDto> conversations
) {}
