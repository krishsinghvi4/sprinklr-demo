package com.example.sprinklr.marketplace.domain.model.insights;

import java.time.Instant;
import java.util.Objects;

public record DashboardConversation(
        String id,
        String userId,
        String sourceConversationId,
        String title,
        String preview,
        int turnCount,
        Instant createdAt,
        Instant updatedAt
) {
    public DashboardConversation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dashboard conversation id must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Dashboard conversation userId must not be blank");
        }
        if (sourceConversationId == null || sourceConversationId.isBlank()) {
            throw new IllegalArgumentException("Dashboard conversation sourceConversationId must not be blank");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
