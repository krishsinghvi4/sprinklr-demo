package com.example.sprinklr.marketplace.domain.model.insights;

import java.util.List;
import java.util.Objects;

public record WidgetBlock(
        int version,
        List<WidgetSpec> widgets
) {
    public WidgetBlock {
        if (version != 1) {
            throw new IllegalArgumentException("Unsupported widget block version: " + version);
        }
        Objects.requireNonNull(widgets, "Widget list must not be null");
        widgets = List.copyOf(widgets);
        if (widgets.isEmpty()) {
            throw new IllegalArgumentException("Widget block must contain at least one widget");
        }
    }
}
