package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "conversations")
@CompoundIndex(name = "userId_updatedAt_idx", def = "{'userId': 1, 'updatedAt': -1}")
public record ConversationDocument(
        @Id String id,
        String userId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {}