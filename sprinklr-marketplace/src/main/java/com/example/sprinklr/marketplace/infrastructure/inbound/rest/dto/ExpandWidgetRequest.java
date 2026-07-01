package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record ExpandWidgetRequest(
        WidgetSpecDto widget,
        String toolResultSnapshot,
        String userFocus
) {}
