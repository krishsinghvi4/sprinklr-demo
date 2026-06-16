package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

public record McpCatalogEntry(
        String id,
        String displayName,
        String description,
        String endpointUrl,
        String serverIdPrefix,
        String authType,
        List<McpCredentialField> credentialFields
) {

    public McpCatalogEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry id must not be blank");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry displayName must not be blank");
        }
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry endpointUrl must not be blank");
        }
        if (serverIdPrefix == null || serverIdPrefix.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry serverIdPrefix must not be blank");
        }
        if (authType == null || authType.isBlank()) {
            throw new IllegalArgumentException("McpCatalogEntry authType must not be blank");
        }
        credentialFields = credentialFields == null ? List.of() : List.copyOf(credentialFields);
    }
}
