package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Ephemeral OAuth state record for CSRF protection and PKCE code_verifier storage.
 * TTL index on expiresAt auto-deletes stale records; successful callbacks delete immediately.
 */
@Document(collection = "mcp_oauth_states")
public record McpOAuthStateDocument(
        @Id String id,
        String userId,
        String catalogServerId,
        String providerKey,
        String codeVerifier,
        Instant createdAt,
        @Indexed(expireAfter = "0s") Instant expiresAt
) {
    public McpOAuthStateDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(catalogServerId, "catalogServerId must not be null");
        Objects.requireNonNull(codeVerifier, "codeVerifier must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        // providerKey may be null for legacy documents — resolved at read time.
    }

    /** Backward-compatible constructor without providerKey. */
    public McpOAuthStateDocument(
            String id,
            String userId,
            String catalogServerId,
            String codeVerifier,
            Instant createdAt,
            Instant expiresAt
    ) {
        this(id, userId, catalogServerId, null, codeVerifier, createdAt, expiresAt);
    }
}
