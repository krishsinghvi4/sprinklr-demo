package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import com.example.sprinklr.marketplace.application.service.UserMcpConfigService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record UserMcpConfigResponseDto(
        @JsonProperty("id") String id,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("description") String description,
        @JsonProperty("endpointUrl") String endpointUrl,
        @JsonProperty("serverIdPrefix") String serverIdPrefix,
        @JsonProperty("headerMode") String headerMode,
        @JsonProperty("credentialFields") List<McpCredentialField> credentialFields,
        @JsonProperty("hasConnectProbe") boolean hasConnectProbe,
        @JsonProperty("hasSkillText") boolean hasSkillText,
        @JsonProperty("createdAt") Instant createdAt
) {
    public static UserMcpConfigResponseDto from(UserMcpConfigService.UserMcpConfigView view) {
        return new UserMcpConfigResponseDto(
                view.id(),
                view.displayName(),
                view.description(),
                view.endpointUrl(),
                view.serverIdPrefix(),
                view.headerMode(),
                view.credentialFields(),
                view.hasConnectProbe(),
                view.hasSkillText(),
                view.createdAt()
        );
    }
}
