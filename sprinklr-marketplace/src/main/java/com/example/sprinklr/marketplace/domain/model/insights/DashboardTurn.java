package com.example.sprinklr.marketplace.domain.model.insights;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record DashboardTurn(
        String id,
        String dashboardConversationId,
        String userId,
        String sourceChatMessageId,
        String prompt,
        String narrative,
        String assistantContent,
        List<WidgetSpec> widgets,
        String toolResultSnapshot,
        Map<String, String> extendedInsights,
        int version,
        String previousVersionId,
        Instant createdAt
) {
    public DashboardTurn {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dashboard turn id must not be blank");
        }
        if (dashboardConversationId == null || dashboardConversationId.isBlank()) {
            throw new IllegalArgumentException("Dashboard turn dashboardConversationId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Dashboard turn userId must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Dashboard turn prompt must not be blank");
        }
        Objects.requireNonNull(widgets, "widgets must not be null");
        widgets = List.copyOf(widgets);
        extendedInsights = extendedInsights == null ? Map.of() : Map.copyOf(extendedInsights);
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("Dashboard turn version must be >= 1");
        }
    }
}
