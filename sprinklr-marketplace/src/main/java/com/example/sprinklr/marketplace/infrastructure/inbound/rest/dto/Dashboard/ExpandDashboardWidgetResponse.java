package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard;

public record ExpandDashboardWidgetResponse(
        String markdown,
        String turnId,
        String widgetId
) {}
