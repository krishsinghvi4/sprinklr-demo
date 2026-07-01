package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record ExpandDashboardWidgetResponse(
        String markdown,
        String turnId,
        String widgetId
) {}
