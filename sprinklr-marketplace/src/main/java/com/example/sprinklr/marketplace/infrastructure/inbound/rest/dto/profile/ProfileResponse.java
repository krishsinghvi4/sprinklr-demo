package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.profile;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.application.service.ProfileService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        UserProfileDto user,
        MarketplaceDto marketplace,
        UsageDto usage
) {

    public static ProfileResponse from(ProfileService.ProfileView view) {
        return new ProfileResponse(
                UserProfileDto.from(view.user()),
                MarketplaceDto.from(view.marketplace()),
                UsageDto.from(view.usage())
        );
    }

    public record UserProfileDto(
            String userId,
            String username,
            String email,
            LocalDateTime createdAt
    ) {
        static UserProfileDto from(ProfileService.UserView user) {
            return new UserProfileDto(user.userId(), user.username(), user.email(), user.createdAt());
        }
    }

    public record MarketplaceDto(
            List<AvailableServerDto> availableServers,
            List<ConnectionDto> connections
    ) {
        static MarketplaceDto from(McpMarketplaceService.MarketplaceView view) {
            return new MarketplaceDto(
                    view.availableServers().stream().map(AvailableServerDto::from).toList(),
                    view.connections().stream().map(ConnectionDto::from).toList()
            );
        }
    }

    public record AvailableServerDto(
            String id,
            String displayName,
            String description,
            String authType,
            String connectMethod,
            List<CredentialFieldDto> credentialFields,
            boolean connected
    ) {
        static AvailableServerDto from(McpMarketplaceService.AvailableServerView server) {
            return new AvailableServerDto(
                    server.id(),
                    server.displayName(),
                    server.description(),
                    server.authType(),
                    server.connectMethod(),
                    server.credentialFields().stream().map(CredentialFieldDto::from).toList(),
                    server.connected()
            );
        }
    }

    public record CredentialFieldDto(String key, String label, String type, boolean required) {
        static CredentialFieldDto from(McpCredentialField field) {
            return new CredentialFieldDto(field.key(), field.label(), field.type(), field.required());
        }
    }

    public record ConnectionDto(
            String id,
            String catalogServerId,
            String displayName,
            String status,
            int toolCount,
            Instant connectedAt,
            boolean hasRedQueryPreferences
    ) {
        static ConnectionDto from(McpMarketplaceService.ConnectionView connection) {
            return new ConnectionDto(
                    connection.id(),
                    connection.catalogServerId(),
                    connection.displayName(),
                    connection.status(),
                    connection.toolCount(),
                    connection.connectedAt(),
                    connection.hasRedQueryPreferences()
            );
        }
    }

    public record UsageDto(
            UsagePeriodDto allTime,
            UsagePeriodDto currentMonth
    ) {
        static UsageDto from(ProfileService.UsageView usage) {
            return new UsageDto(
                    UsagePeriodDto.from(usage.allTime()),
                    UsagePeriodDto.from(usage.currentMonth())
            );
        }
    }

    public record UsagePeriodDto(
            long totalTokens,
            long promptTokens,
            long completionTokens,
            BigDecimal estimatedCostUsd,
            long llmCallCount
    ) {
        static UsagePeriodDto from(ProfileService.UsagePeriodView period) {
            return new UsagePeriodDto(
                    period.totalTokens(),
                    period.promptTokens(),
                    period.completionTokens(),
                    period.estimatedCostUsd(),
                    period.llmCallCount()
            );
        }
    }
}
