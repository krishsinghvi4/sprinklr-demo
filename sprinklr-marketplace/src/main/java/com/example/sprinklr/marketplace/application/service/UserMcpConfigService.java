package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectProbeConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialHeaderMode;
import com.example.sprinklr.marketplace.domain.model.MCP.UserMcpConfig;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.UserMcpConfigPort;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class UserMcpConfigService {

    private static final Pattern SERVER_ID_PREFIX_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]{1,31}$");

    private final UserMcpConfigPort userMcpConfigPort;
    private final McpRegistryPort registryPort;

    public UserMcpConfigService(UserMcpConfigPort userMcpConfigPort, McpRegistryPort registryPort) {
        this.userMcpConfigPort = userMcpConfigPort;
        this.registryPort = registryPort;
    }

    public List<UserMcpConfigView> listConfigs(String userId) {
        return userMcpConfigPort.findByUserId(userId).stream()
                .map(this::toView)
                .toList();
    }

    public UserMcpConfigView createConfig(String userId, CreateUserMcpConfigRequest request) {
        validateRequest(request);

        if (userMcpConfigPort.findByUserIdAndServerIdPrefix(userId, request.serverIdPrefix()).isPresent()) {
            throw new IllegalArgumentException(
                    "A custom MCP with serverIdPrefix '" + request.serverIdPrefix() + "' already exists");
        }

        UserMcpConfig config = new UserMcpConfig(
                UUID.randomUUID().toString(),
                userId,
                request.displayName().trim(),
                request.description() == null ? "" : request.description().trim(),
                request.endpointUrl().trim(),
                request.serverIdPrefix().trim(),
                request.tokenField().trim(),
                request.headerMode(),
                request.customHeaderName(),
                request.credentialFields(),
                request.connectProbe(),
                request.skillText(),
                Instant.now()
        );

        return toView(userMcpConfigPort.save(config));
    }

    public void deleteConfig(String userId, String configId) {
        UserMcpConfig config = userMcpConfigPort.findByIdAndUserId(configId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Custom MCP config not found"));

        boolean hasConnection = registryPort.findByUserId(userId).stream()
                .anyMatch(connection -> connection.catalogServerId().equals(config.id()));
        if (hasConnection) {
            throw new IllegalArgumentException(
                    "Disconnect this MCP server before deleting its configuration");
        }

        userMcpConfigPort.deleteByIdAndUserId(configId, userId);
    }

    private void validateRequest(CreateUserMcpConfigRequest request) {
        if (request.displayName() == null || request.displayName().isBlank()) {
            throw new IllegalArgumentException("displayName is required");
        }
        if (request.endpointUrl() == null || request.endpointUrl().isBlank()) {
            throw new IllegalArgumentException("endpointUrl is required");
        }
        validateEndpointUrl(request.endpointUrl().trim());
        if (request.serverIdPrefix() == null || !SERVER_ID_PREFIX_PATTERN.matcher(request.serverIdPrefix().trim()).matches()) {
            throw new IllegalArgumentException(
                    "serverIdPrefix must start with a lowercase letter and contain only lowercase letters, numbers, underscores, or hyphens (2-32 chars)");
        }
        if (request.tokenField() == null || request.tokenField().isBlank()) {
            throw new IllegalArgumentException("tokenField is required");
        }
        if (request.headerMode() == null) {
            throw new IllegalArgumentException("headerMode is required");
        }
        if (request.headerMode() == McpCredentialHeaderMode.CUSTOM
                && (request.customHeaderName() == null || request.customHeaderName().isBlank())) {
            throw new IllegalArgumentException("customHeaderName is required when headerMode is CUSTOM");
        }
        if (request.credentialFields() != null) {
            for (McpCredentialField field : request.credentialFields()) {
                if (field.key() == null || field.key().isBlank()) {
                    throw new IllegalArgumentException("credentialFields[].key is required");
                }
            }
        }
        if (request.connectProbe() != null) {
            McpConnectProbeConfig probe = request.connectProbe();
            if (probe.tool() == null || probe.tool().isBlank()) {
                throw new IllegalArgumentException("connectProbe.tool is required when connectProbe is provided");
            }
            if (probe.failureMessage() == null || probe.failureMessage().isBlank()) {
                throw new IllegalArgumentException("connectProbe.failureMessage is required when connectProbe is provided");
            }
        }
    }

    private static void validateEndpointUrl(String endpointUrl) {
        try {
            URI uri = URI.create(endpointUrl);
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new IllegalArgumentException("endpointUrl must use http or https");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("endpointUrl must include a host");
            }
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage().startsWith("endpointUrl")) {
                throw exception;
            }
            throw new IllegalArgumentException("endpointUrl is not a valid URL");
        }
    }

    private UserMcpConfigView toView(UserMcpConfig config) {
        return new UserMcpConfigView(
                config.id(),
                config.displayName(),
                config.description(),
                config.endpointUrl(),
                config.serverIdPrefix(),
                config.headerMode().name(),
                config.credentialFields(),
                config.connectProbe() != null,
                config.skillText() != null && !config.skillText().isBlank(),
                config.createdAt()
        );
    }

    public record CreateUserMcpConfigRequest(
            String displayName,
            String description,
            String endpointUrl,
            String serverIdPrefix,
            String tokenField,
            McpCredentialHeaderMode headerMode,
            String customHeaderName,
            List<McpCredentialField> credentialFields,
            McpConnectProbeConfig connectProbe,
            String skillText
    ) {}

    public record UserMcpConfigView(
            String id,
            String displayName,
            String description,
            String endpointUrl,
            String serverIdPrefix,
            String headerMode,
            List<McpCredentialField> credentialFields,
            boolean hasConnectProbe,
            boolean hasSkillText,
            Instant createdAt
    ) {}
}
