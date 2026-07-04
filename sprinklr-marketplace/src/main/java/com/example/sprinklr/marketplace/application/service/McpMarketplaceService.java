package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.application.service.mcp.AuthFlowRouter;
import com.example.sprinklr.marketplace.application.service.mcp.CredentialAuthFlowHandler;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
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

    private final McpCatalogLoader catalogLoader;
    private final McpRegistryPort registryPort;
    private final CredentialAuthFlowHandler credentialAuthFlowHandler;
    private final AuthFlowRouter authFlowRouter;

    public McpMarketplaceService(
            McpCatalogLoader catalogLoader,
            McpRegistryPort registryPort,
            CredentialAuthFlowHandler credentialAuthFlowHandler,
            AuthFlowRouter authFlowRouter
    ) {
        this.catalogLoader = catalogLoader;
        this.registryPort = registryPort;
        this.credentialAuthFlowHandler = credentialAuthFlowHandler;
        this.authFlowRouter = authFlowRouter;
    }

    /**
     * Builds the marketplace view by combining catalog entries with user connections.
     */
    public MarketplaceView getMarketplaceForUser(String userId) {
        List<McpUserConnection> connections = registryPort.findByUserId(userId);
        List<AvailableServerView> available = catalogLoader.getAll().stream()
                .map(entry -> toAvailableServer(entry, connections))
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
        McpCatalogEntry catalogEntry = catalogLoader.findById(catalogServerId)
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

    private void assertRedConnection(String userId, String connectionId) {
        McpUserConnection connection = registryPort.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Connection not found"));
        if (!isRedConnection(connection)) {
            throw new IllegalArgumentException("RED query preferences apply only to RED connections");
        }
    }

    private boolean isRedConnection(McpUserConnection connection) {
        return catalogLoader.findById(connection.catalogServerId())
                .map(entry -> "red".equals(entry.serverIdPrefix()))
                .orElse(false);
    }

    private AvailableServerView toAvailableServer(McpCatalogEntry entry, List<McpUserConnection> connections) {
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
                connected
        );
    }

    private ConnectionView toConnectionView(McpUserConnection connection) {
        String displayName = catalogLoader.findById(connection.catalogServerId())
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
            List<com.example.sprinklr.marketplace.domain.model.McpCredentialField> credentialFields,
            boolean connected
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
