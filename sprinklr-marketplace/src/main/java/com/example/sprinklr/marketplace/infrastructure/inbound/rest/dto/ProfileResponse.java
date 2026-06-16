package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.application.service.ProfileService;
import com.example.sprinklr.marketplace.domain.model.McpCredentialField;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public record ProfileResponse(
        UserProfileDto user,
        MarketplaceDto marketplace
) {

    public static ProfileResponse from(ProfileService.ProfileView view) {
        return new ProfileResponse(
                UserProfileDto.from(view.user()),
                MarketplaceDto.from(view.marketplace())
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
            List<CredentialFieldDto> credentialFields,
            boolean connected
    ) {
        static AvailableServerDto from(McpMarketplaceService.AvailableServerView server) {
            return new AvailableServerDto(
                    server.id(),
                    server.displayName(),
                    server.description(),
                    server.authType(),
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
            Instant connectedAt
    ) {
        static ConnectionDto from(McpMarketplaceService.ConnectionView connection) {
            return new ConnectionDto(
                    connection.id(),
                    connection.catalogServerId(),
                    connection.displayName(),
                    connection.status(),
                    connection.toolCount(),
                    connection.connectedAt()
            );
        }
    }
}
