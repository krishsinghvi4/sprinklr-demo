package com.example.sprinklr.marketplace.domain.model.insights;

import java.util.Map;
import java.util.Objects;

public record WidgetSpec(
        String id,
        String type,
        String title,
        String description,
        Map<String, Object> data
) {
    public WidgetSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Widget id must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Widget type must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Widget title must not be blank");
        }
        Objects.requireNonNull(data, "Widget data must not be null");
    }
}
