package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document(collection = "mcp_dcr_clients")
public record McpDcrClientDocument(
        @Id String id,
        @Indexed(unique = true) String redirectUri,
        String clientId,
        String clientSecret,
        Instant registeredAt
) {
    public McpDcrClientDocument {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(redirectUri, "redirectUri must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(clientSecret, "clientSecret must not be null");
        Objects.requireNonNull(registeredAt, "registeredAt must not be null");
    }
}
