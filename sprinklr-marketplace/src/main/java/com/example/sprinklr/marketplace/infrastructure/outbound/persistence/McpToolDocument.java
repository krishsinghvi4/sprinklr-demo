package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import java.util.List;

public record McpToolDocument(
        String name,
        String description,
        String serverId,
        String inputSchemaJson
) {}
