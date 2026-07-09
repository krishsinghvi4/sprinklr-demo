package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard;

import java.util.Map;

public record WidgetSpecDto(
        String id,
        String type,
        String title,
        String description,
        Map<String, Object> data
) {}
