package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import com.example.sprinklr.marketplace.domain.model.MCP.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialAuthConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.UserMcpConfig;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.UserMcpConfigPort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Merges the static classpath MCP catalog with per-user custom MCP configurations.
 */
@Component
public class MergedCatalogResolver {

    private final McpCatalogLoader catalogLoader;
    private final UserMcpConfigPort userMcpConfigPort;

    public MergedCatalogResolver(McpCatalogLoader catalogLoader, UserMcpConfigPort userMcpConfigPort) {
        this.catalogLoader = catalogLoader;
        this.userMcpConfigPort = userMcpConfigPort;
    }

    public List<McpCatalogEntry> getAll(String userId) {
        List<McpCatalogEntry> merged = new ArrayList<>(catalogLoader.getAll());
        userMcpConfigPort.findByUserId(userId).stream()
                .map(this::toCatalogEntry)
                .forEach(merged::add);
        return List.copyOf(merged);
    }

    public Optional<McpCatalogEntry> findById(String id, String userId) {
        Optional<McpCatalogEntry> staticEntry = catalogLoader.findById(id);
        if (staticEntry.isPresent()) {
            return staticEntry;
        }
        return userMcpConfigPort.findByIdAndUserId(id, userId)
                .map(this::toCatalogEntry);
    }

    public boolean isUserDefined(String catalogServerId, String userId) {
        return catalogLoader.findById(catalogServerId).isEmpty()
                && userMcpConfigPort.findByIdAndUserId(catalogServerId, userId).isPresent();
    }

    public McpCatalogEntry toCatalogEntry(UserMcpConfig config) {
        McpAuthConfig authConfig = new McpAuthConfig(
                McpAuthKind.CREDENTIALS,
                null,
                new McpCredentialAuthConfig(
                        config.tokenField(),
                        config.headerMode(),
                        null,
                        config.customHeaderName()
                )
        );

        List<com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField> credentialFields =
                config.credentialFields().isEmpty()
                        ? List.of(new com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField(
                        config.tokenField(),
                        "Access token",
                        "password",
                        true
                ))
                        : config.credentialFields();

        return new McpCatalogEntry(
                config.id(),
                config.displayName(),
                config.description() == null ? "" : config.description(),
                config.endpointUrl(),
                config.serverIdPrefix(),
                "CREDENTIALS",
                authConfig,
                McpConnectMethod.CREDENTIAL_FORM,
                credentialFields,
                null,
                config.connectProbe(),
                null
        );
    }
}
