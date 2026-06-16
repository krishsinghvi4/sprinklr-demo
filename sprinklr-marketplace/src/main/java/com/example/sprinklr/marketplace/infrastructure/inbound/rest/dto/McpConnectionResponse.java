package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;

import java.time.Instant;

public record McpConnectionResponse(
        String id,
        String catalogServerId,
        String displayName,
        String status,
        int toolCount,
        Instant connectedAt
) {

    public static McpConnectionResponse from(McpMarketplaceService.ConnectionView view) {
        return new McpConnectionResponse(
                view.id(),
                view.catalogServerId(),
                view.displayName(),
                view.status(),
                view.toolCount(),
                view.connectedAt()
        );
    }
}
