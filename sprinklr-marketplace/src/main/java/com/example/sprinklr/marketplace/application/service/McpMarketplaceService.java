package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpDiscoveryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpOAuthException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the MCP marketplace lifecycle: list servers, connect/disconnect,
 * and persist user connections with discovered tool metadata.
 */
@Service
public class McpMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(McpMarketplaceService.class);

    private final McpCatalogLoader catalogLoader;
    private final McpRegistryPort registryPort;
    private final McpDiscoveryPort discoveryPort;
    private final CredentialVaultPort credentialVault;
    private final McpAuthStrategyRegistry authStrategyRegistry;

    public McpMarketplaceService(
            McpCatalogLoader catalogLoader,
            McpRegistryPort registryPort,
            McpDiscoveryPort discoveryPort,
            CredentialVaultPort credentialVault,
            McpAuthStrategyRegistry authStrategyRegistry
    ) {
        this.catalogLoader = catalogLoader;
        this.registryPort = registryPort;
        this.discoveryPort = discoveryPort;
        this.credentialVault = credentialVault;
        this.authStrategyRegistry = authStrategyRegistry;
    }
//It fetches existing user connections from the registryPort and compares them against the list of all available MCP servers provided by catalogLoader
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

        //Establish a new link between the user and an external MCP server by creating a new connection
    /**
     * Connects to a non-OAuth MCP server by validating credentials, discovering tools,
     * and storing the resulting connection.
     */
    public ConnectionView connect(String userId, String catalogServerId, Map<String, String> credentials) {
        McpCatalogEntry catalogEntry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));

        if ("OAUTH_ATLASSIAN".equals(catalogEntry.authType())) {
            throw new McpOAuthException(
                    "OAuth connection required for " + catalogEntry.displayName(),
                    "OAuth required for catalogServerId=" + catalogServerId);
        }

        validateCredentials(catalogEntry, credentials);

        Optional<McpUserConnection> existing = registryPort.findByUserIdAndCatalogServerId(userId, catalogServerId);
        if (existing.isPresent()) {
            disconnect(userId, existing.get().id());
        }

        String connectionId = UUID.randomUUID().toString();
        Map<String, String> authHeaders = authStrategyRegistry 
                .require(catalogEntry.authType())
                .buildAuthHeaders(credentials);

        log.info("[MCP] Connecting userId={} catalogServerId={} connectionId={}",
                userId, catalogServerId, connectionId);

        McpDiscoveryPort.McpDiscoveryResult discoveryResult;
        try {
            discoveryResult = discoveryPort.discover(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    catalogEntry.serverIdPrefix(),
                    connectionId
            );
        } catch (McpConnectionException | McpDiscoveryException exception) {
            log.warn("[MCP] Connect failed userId={} catalogServerId={}: {}",
                    userId, catalogServerId, exception.getMessage());
            throw exception;
        }

        String encrypted = credentialVault.encrypt(credentials);
        McpUserConnection connection = new McpUserConnection(
                connectionId,
                userId,
                catalogServerId,
                McpConnectionStatus.CONNECTED,
                discoveryResult.sessionId(),
                discoveryResult.protocolVersion(),
                discoveryResult.tools(),
                Instant.now(),
                null
        );

        McpUserConnection saved = registryPort.saveConnection(
                connection,
                encrypted,
                catalogEntry.serverIdPrefix()
        );
        log.info("[MCP] Connected userId={} connectionId={} toolCount={}",
                userId, connectionId, saved.tools().size());
        return toConnectionView(saved);
    }

    /**
     * Connects an OAuth-backed MCP server after the OAuth callback exchanges tokens.
     */
    public ConnectionView connectOAuth(String userId, String catalogServerId, Map<String, String> credentials) {
        McpCatalogEntry catalogEntry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));

        if (!"OAUTH_ATLASSIAN".equals(catalogEntry.authType())) {
            throw new McpOAuthException(
                    "OAuth is not enabled for " + catalogEntry.displayName(),
                    "OAuth connect attempted for authType=" + catalogEntry.authType());
        }

        Optional<McpUserConnection> existing = registryPort.findByUserIdAndCatalogServerId(userId, catalogServerId);
        if (existing.isPresent()) {
            disconnect(userId, existing.get().id());
        }

        String connectionId = UUID.randomUUID().toString();
        Map<String, String> authHeaders = authStrategyRegistry
                .require(catalogEntry.authType())
                .buildAuthHeaders(credentials);

        log.info("[MCP] Connecting OAuth userId={} catalogServerId={} connectionId={}",
                userId, catalogServerId, connectionId);

        McpDiscoveryPort.McpDiscoveryResult discoveryResult;
        try {
            discoveryResult = discoveryPort.discover(
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    catalogEntry.serverIdPrefix(),
                    connectionId
            );
        } catch (McpConnectionException | McpDiscoveryException exception) {
            log.warn("[MCP] OAuth connect failed userId={} catalogServerId={}: {}",
                    userId, catalogServerId, exception.getMessage());
            throw exception;
        }

        String encrypted = credentialVault.encrypt(credentials);
        McpUserConnection connection = new McpUserConnection(
                connectionId,
                userId,
                catalogServerId,
                McpConnectionStatus.CONNECTED,
                discoveryResult.sessionId(),
                discoveryResult.protocolVersion(),
                discoveryResult.tools(),
                Instant.now(),
                null
        );

        McpUserConnection saved = registryPort.saveConnection(
                connection,
                encrypted,
                catalogEntry.serverIdPrefix()
        );
        log.info("[MCP] OAuth connected userId={} connectionId={} toolCount={}",
                userId, connectionId, saved.tools().size());
        return toConnectionView(saved);
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

    private void validateCredentials(McpCatalogEntry entry, Map<String, String> credentials) {
        entry.credentialFields().forEach(field -> {
            if (field.required()) {
                String value = credentials.get(field.key());
                if (value == null || value.isBlank()) {
                    throw new IllegalArgumentException(field.label() + " is required");
                }
            }
        });
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
                entry.credentialFields(),
                connected
        );
    }

    private ConnectionView toConnectionView(McpUserConnection connection) {
        String displayName = catalogLoader.findById(connection.catalogServerId())
                .map(McpCatalogEntry::displayName)
                .orElse(connection.catalogServerId());

        return new ConnectionView(
                connection.id(),
                connection.catalogServerId(),
                displayName,
                connection.status().name(),
                connection.tools().size(),
                connection.connectedAt()
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
            List<com.example.sprinklr.marketplace.domain.model.McpCredentialField> credentialFields,
            boolean connected
    ) {}

    public record ConnectionView(
            String id,
            String catalogServerId,
            String displayName,
            String status,
            int toolCount,
            Instant connectedAt
    ) {}
}
