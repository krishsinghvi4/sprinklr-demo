package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpDiscoveryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Shared connect orchestration: discovery handshake, credential encryption, registry persistence.
 * Used by both OAuth callback and credential-form connect flows.
 */
@Component
public class McpConnectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionOrchestrator.class);

    private final McpCatalogLoader catalogLoader;
    private final McpRegistryPort registryPort;
    private final McpDiscoveryPort discoveryPort;
    private final CredentialVaultPort credentialVault;
    private final McpProviderResolver providerResolver;

    public McpConnectionOrchestrator(
            McpCatalogLoader catalogLoader,
            McpRegistryPort registryPort,
            McpDiscoveryPort discoveryPort,
            CredentialVaultPort credentialVault,
            McpProviderResolver providerResolver
    ) {
        this.catalogLoader = catalogLoader;
        this.registryPort = registryPort;
        this.discoveryPort = discoveryPort;
        this.credentialVault = credentialVault;
        this.providerResolver = providerResolver;
    }

    public McpMarketplaceService.ConnectionView connectWithCredentials(
            String userId,
            String catalogServerId,
            Map<String, String> credentials
    ) {
        McpCatalogEntry catalogEntry = catalogLoader.findById(catalogServerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));

        McpProvider provider = providerResolver.resolve(catalogEntry);

        Optional<McpUserConnection> existing = registryPort.findByUserIdAndCatalogServerId(userId, catalogServerId);
        if (existing.isPresent()) {
            registryPort.delete(existing.get().id(), userId);
        }

        String connectionId = UUID.randomUUID().toString();
        Map<String, String> authHeaders = provider.buildAuthHeaders(catalogEntry, credentials);

        log.info("[MCP] Connecting userId={} catalogServerId={} connectionId={} authKind={}",
                userId, catalogServerId, connectionId, catalogEntry.authConfig().kind());

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

        return toConnectionView(saved, catalogEntry);
    }

    private McpMarketplaceService.ConnectionView toConnectionView(
            McpUserConnection connection,
            McpCatalogEntry catalogEntry
    ) {
        return new McpMarketplaceService.ConnectionView(
                connection.id(),
                connection.catalogServerId(),
                catalogEntry.displayName(),
                connection.status().name(),
                connection.tools().size(),
                connection.connectedAt()
        );
    }
}
