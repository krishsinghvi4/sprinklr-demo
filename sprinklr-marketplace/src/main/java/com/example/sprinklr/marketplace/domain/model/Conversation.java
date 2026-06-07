package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.Objects;

public record Conversation(
        String id,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public Conversation {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Conversation id must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("Conversation userId must not be blank");
        }
        Objects.requireNonNull(createdAt, "Conversation createdAt must not be null");
        Objects.requireNonNull(updatedAt, "Conversation updatedAt must not be null");
    }
}
