package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

public record McpToolDocument(
        String name,
        String description,
        String serverId,
        String inputSchemaJson
) {}
