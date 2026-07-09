package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "user_mcp_configs")
@CompoundIndex(name = "user_server_prefix", def = "{'userId': 1, 'serverIdPrefix': 1}", unique = true)
public record UserMcpConfigDocument(
        @Id String id,
        String userId,
        String displayName,
        String description,
        String endpointUrl,
        String serverIdPrefix,
        String tokenField,
        String headerMode,
        String customHeaderName,
        List<CredentialFieldDocument> credentialFields,
        ConnectProbeDocument connectProbe,
        String skillText,
        Instant createdAt
) {

    public record CredentialFieldDocument(
            String key,
            String label,
            String type,
            boolean required
    ) {}

    public record ConnectProbeDocument(
            String tool,
            String argumentsJson,
            String failureMessage
    ) {}
}
