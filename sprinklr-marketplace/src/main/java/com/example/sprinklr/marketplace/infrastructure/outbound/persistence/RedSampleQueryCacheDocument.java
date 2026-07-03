package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Cached full result for a RED sample query keyed by user, connection, tool, and scope args.
 */
@Document(collection = "red_sample_query_cache")
public record RedSampleQueryCacheDocument(
        @Id String id,
        String userId,
        String connectionId,
        String toolName,
        String scopeArgsJson,
        String resultContent,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {}
