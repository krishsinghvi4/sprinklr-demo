package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.application.service.mcp.AuthFlowRouter;
import com.example.sprinklr.marketplace.application.service.mcp.CredentialAuthFlowHandler;
import com.example.sprinklr.marketplace.application.service.mcp.TeamsWebhookTokenService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialField;
import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.MergedCatalogResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the MCP marketplace lifecycle: list servers, connect/disconnect,
 * and persist user connections with discovered tool metadata.
 */
@Service
public class McpMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(McpMarketplaceService.class);

    private final MergedCatalogResolver catalogResolver;
    private final McpRegistryPort registryPort;
    private final CredentialAuthFlowHandler credentialAuthFlowHandler;
    private final AuthFlowRouter authFlowRouter;
    private final CredentialVaultPort credentialVault;
    private final TeamsWebhookTokenService teamsWebhookTokenService;

    public McpMarketplaceService(
            MergedCatalogResolver catalogResolver,
            McpRegistryPort registryPort,
            CredentialAuthFlowHandler credentialAuthFlowHandler,
            AuthFlowRouter authFlowRouter,
            CredentialVaultPort credentialVault,
            TeamsWebhookTokenService teamsWebhookTokenService
    ) {
        this.catalogResolver = catalogResolver;
        this.registryPort = registryPort;
        this.credentialAuthFlowHandler = credentialAuthFlowHandler;
        this.authFlowRouter = authFlowRouter;
        this.credentialVault = credentialVault;
        this.teamsWebhookTokenService = teamsWebhookTokenService;
    }

    /**
     * Builds the marketplace view by combining catalog entries with user connections.
     */
    public MarketplaceView getMarketplaceForUser(String userId) {
        List<McpUserConnection> connections = registryPort.findByUserId(userId);
        List<AvailableServerView> available = catalogResolver.getAll(userId).stream()
                .map(entry -> toAvailableServer(userId, entry, connections))
                .toList();
        List<ConnectionView> connectionViews = connections.stream()
                .map(this::toConnectionView)
                .toList();
        return new MarketplaceView(available, connectionViews);
    }

    /**
     * Connects to a credential-based MCP server by validating credentials, discovering tools,
     * and storing the resulting connection.
     */
    public ConnectionView connect(String userId, String catalogServerId, Map<String, String> credentials) {
        McpCatalogEntry catalogEntry = catalogResolver.findById(catalogServerId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));

        authFlowRouter.assertCredentialConnectAllowed(catalogEntry);
        return credentialAuthFlowHandler.connect(userId, catalogEntry, credentials);
    }

    /**
     * Removes the persisted MCP connection for the given user.
     */
    public void disconnect(String userId, String connectionId) {
        log.info("[MCP] Disconnecting userId={} connectionId={}", userId, connectionId);
        registryPort.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        registryPort.delete(connectionId, userId);
        boolean stillExists = registryPort.findByIdAndUserId(connectionId, userId).isPresent();
        if (stillExists) {
            throw new IllegalStateException("Failed to delete connection");
        }
    }

    public RedQueryPreferences getRedQueryPreferences(String userId, String connectionId) {
        assertRedConnection(userId, connectionId);
        return registryPort.findRedQueryPreferences(userId, connectionId)
                .orElseGet(() -> new RedQueryPreferences(List.of(), List.of()));
    }

    public RedQueryPreferences updateRedQueryPreferences(
            String userId,
            String connectionId,
            RedQueryPreferences preferences
    ) {
        assertRedConnection(userId, connectionId);
        registryPort.updateRedQueryPreferences(userId, connectionId, preferences);
        return preferences;
    }

    public String getTeamsWebhookUrl(String userId, String connectionId) {
        McpUserConnection connection = registryPort.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        if (!isTeamsConnection(connection)) {
            throw new IllegalArgumentException("Teams webhook URL applies only to Teams Messages connections");
        }
        String encrypted = registryPort.findEncryptedCredentials(userId, connectionId)
                .orElseThrow(() -> new IllegalArgumentException("Connection credentials not found"));
        try {
            Map<String, String> credentials = credentialVault.decrypt(encrypted);
            String webhookToken = credentials.get("webhookToken");
            if (webhookToken != null && !webhookToken.isBlank()) {
                return teamsWebhookTokenService.buildWebhookUrl(webhookToken);
            }
            String webhookUrl = credentials.get("webhookUrl");
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                return webhookUrl;
            }
        } catch (IllegalStateException exception) {
            log.warn("[MCP] Could not decrypt Teams credentials connectionId={} — regenerating webhook URL",
                    connectionId);
        }
        String token = teamsWebhookTokenService.generateToken(userId);
        return teamsWebhookTokenService.buildWebhookUrl(token);
    }

    private boolean isTeamsConnection(McpUserConnection connection) {
        return catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .map(entry -> "teams".equals(entry.serverIdPrefix()))
                .orElse(false);
    }

    private void assertRedConnection(String userId, String connectionId) {
        McpUserConnection connection = registryPort.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        if (!isRedConnection(connection)) {
            throw new IllegalArgumentException("RED query preferences apply only to RED connections");
        }
    }

    private boolean isRedConnection(McpUserConnection connection) {
        return catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .map(entry -> "red".equals(entry.serverIdPrefix()))
                .orElse(false);
    }

    private AvailableServerView toAvailableServer(
            String userId,
            McpCatalogEntry entry,
            List<McpUserConnection> connections
    ) {
        boolean connected = connections.stream()
                .anyMatch(c -> c.catalogServerId().equals(entry.id())
                        && c.status() == McpConnectionStatus.CONNECTED);
        return new AvailableServerView(
                entry.id(),
                entry.displayName(),
                entry.description(),
                entry.authType(),
                entry.connectMethod().name(),
                entry.credentialFields(),
                connected,
                catalogResolver.isUserDefined(entry.id(), userId)
        );
    }

    private ConnectionView toConnectionView(McpUserConnection connection) {
        String displayName = catalogResolver.findById(connection.catalogServerId(), connection.userId())
                .map(McpCatalogEntry::displayName)
                .orElse(connection.catalogServerId());
        boolean hasRedQueryPreferences = isRedConnection(connection)
                && registryPort.findRedQueryPreferences(connection.userId(), connection.id())
                .map(RedQueryPreferences::hasConfiguredValues)
                .orElse(false);

        return new ConnectionView(
                connection.id(),
                connection.catalogServerId(),
                displayName,
                connection.status().name(),
                connection.tools().size(),
                connection.connectedAt(),
                hasRedQueryPreferences
        );
    }

    public record MarketplaceView(
            List<AvailableServerView> availableServers,
            List<ConnectionView> connections
    ) {}

    public record AvailableServerView(
            String id,
            String displayName,
            String description,
            String authType,
            String connectMethod,
            List<McpCredentialField> credentialFields,
            boolean connected,
            boolean userDefined
    ) {}

    public record ConnectionView(
            String id,
            String catalogServerId,
            String displayName,
            String status,
            int toolCount,
            Instant connectedAt,
            boolean hasRedQueryPreferences
    ) {}
}
