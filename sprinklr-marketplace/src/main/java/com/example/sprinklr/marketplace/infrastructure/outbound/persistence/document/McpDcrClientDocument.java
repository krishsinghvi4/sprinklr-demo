package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

/**
 * Stores dynamically registered OAuth clients (DCR) per provider and redirect URI.
 * providerKey scopes DCR clients when multiple OAuth issuers share the same redirect URI.
 */
@Document(collection = "mcp_dcr_clients")
@CompoundIndex(name = "provider_redirect_unique", def = "{'providerKey': 1, 'redirectUri': 1}", unique = true)
public record McpDcrClientDocument(
        @Id String id,
        String providerKey,
        @Indexed String redirectUri,
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
        // providerKey may be null for documents created before multi-provider DCR support.
    }

    /** Legacy constructor for documents created before providerKey was introduced. */
    public McpDcrClientDocument(String id, String redirectUri, String clientId, String clientSecret, Instant registeredAt) {
        this(id, "legacy", redirectUri, clientId, clientSecret, registeredAt);
    }
}
