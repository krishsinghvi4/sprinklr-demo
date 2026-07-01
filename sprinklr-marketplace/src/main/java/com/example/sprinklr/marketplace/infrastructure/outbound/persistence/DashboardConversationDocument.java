package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "dashboard_conversations")
@CompoundIndex(name = "userId_updatedAt_idx", def = "{'userId': 1, 'updatedAt': -1}")
@CompoundIndex(name = "userId_sourceConversationId_idx", def = "{'userId': 1, 'sourceConversationId': 1}", unique = true)
public record DashboardConversationDocument(
        @Id String id,
        String userId,
        String sourceConversationId,
        String title,
        String preview,
        int turnCount,
        Instant createdAt,
        Instant updatedAt
) {}
