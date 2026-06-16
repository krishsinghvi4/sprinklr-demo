package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "mcp_connections")
@CompoundIndex(name = "user_catalog", def = "{'userId': 1, 'catalogServerId': 1}", unique = true)
public record McpConnectionDocument(
        @Id String id,
        String userId,
        String catalogServerId,
        String serverIdPrefix,
        String encryptedCredentials,
        String mcpSessionId,
        String mcpProtocolVersion,
        String status,
        List<McpToolDocument> tools,
        Instant connectedAt,
        String lastError
) {}
