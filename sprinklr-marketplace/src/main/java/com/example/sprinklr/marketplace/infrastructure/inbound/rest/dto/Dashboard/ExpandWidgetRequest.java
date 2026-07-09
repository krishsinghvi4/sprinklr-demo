package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard;

public record ExpandWidgetRequest(
        WidgetSpecDto widget,
        String toolResultSnapshot,
        String userFocus
) {}
