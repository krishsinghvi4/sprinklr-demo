package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/**
 * Cross-turn continuation state for an in-progress multi-step tool workflow, one per conversation.
 * The {@code expiresAt} field carries a TTL index so Mongo reaps stale state automatically.
 */
@Document(collection = "pending_workflows")
public record PendingWorkflowDocument(
        @Id String conversationId,
        String userId,
        List<String> serverPrefixes,
        List<String> awaitingGoalTools,
        List<String> satisfiedToolNames,
        List<String> toolResultSummaries,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {}
