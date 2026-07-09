package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import com.example.sprinklr.marketplace.application.service.UserMcpConfigService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialHeaderMode;

import java.time.Instant;
import java.util.List;

public record CreateUserMcpConfigRequestDto(
        String displayName,
        String description,
        String endpointUrl,
        String serverIdPrefix,
        String tokenField,
        String headerMode,
        String customHeaderName,
        List<McpCredentialField> credentialFields,
        ConnectProbeRequestDto connectProbe,
        String skillText
) {
    public UserMcpConfigService.CreateUserMcpConfigRequest toDomain() {
        String resolvedHeaderMode = headerMode == null || headerMode.isBlank() ? "BEARER" : headerMode;
        return new UserMcpConfigService.CreateUserMcpConfigRequest(
                displayName,
                description,
                endpointUrl,
                serverIdPrefix,
                tokenField,
                McpCredentialHeaderMode.valueOf(resolvedHeaderMode),
                customHeaderName,
                credentialFields,
                connectProbe == null ? null : connectProbe.toDomain(),
                skillText
        );
    }
}
