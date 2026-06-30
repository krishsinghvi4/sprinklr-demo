package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.McpToolSelectionConfig;
import com.example.sprinklr.marketplace.domain.model.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpDiscoveryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolDependencyGraphPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect.CompositeMcpConnectValidationAdapter;
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
    private final CompositeMcpConnectValidationAdapter connectValidationAdapter;
    private final ToolDependencyGraphPort toolDependencyGraphPort;
    private final McpProperties mcpProperties;

    public McpConnectionOrchestrator(
            McpCatalogLoader catalogLoader,
            McpRegistryPort registryPort,
            McpDiscoveryPort discoveryPort,
            CredentialVaultPort credentialVault,
            McpProviderResolver providerResolver,
            CompositeMcpConnectValidationAdapter connectValidationAdapter,
            ToolDependencyGraphPort toolDependencyGraphPort,
            McpProperties mcpProperties
    ) {
        this.catalogLoader = catalogLoader;
        this.registryPort = registryPort;
        this.discoveryPort = discoveryPort;
        this.credentialVault = credentialVault;
        this.providerResolver = providerResolver;
        this.connectValidationAdapter = connectValidationAdapter;
        this.toolDependencyGraphPort = toolDependencyGraphPort;
        this.mcpProperties = mcpProperties;
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

        try {
            connectValidationAdapter.validateLiveConnection(
                    catalogEntry,
                    catalogEntry.endpointUrl(),
                    authHeaders,
                    discoveryResult.sessionId(),
                    discoveryResult.protocolVersion()
            );
        } catch (McpConnectionException exception) {
            log.warn("[MCP] Connect validation failed userId={} catalogServerId={}: {}",
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

        generateAndStoreDependencyGraph(saved, catalogEntry);

        return toConnectionView(saved, catalogEntry);
    }

    /**
     * Generates the per-server tool dependency graph once at connect time and persists it on the
     * connection. Failures never break connect: the graph generator returns a FAILED graph and chat
     * degrades to router-only tool selection for this server.
     */
    private void generateAndStoreDependencyGraph(McpUserConnection saved, McpCatalogEntry catalogEntry) {
        if (!mcpProperties.getToolSelection().isGenerateGraphOnConnect()) {
            log.info("[MCP] Dependency-graph generation disabled — skipping for connectionId={}", saved.id());
            return;
        }
        try {
            ToolDependencyGraph graph;
            if (shouldSkipDependencyGraph(catalogEntry)) {
                log.info("[MCP] Dependency-graph generation skipped for prefix={} (catalog skipDependencyGraph)",
                        catalogEntry.serverIdPrefix());
                graph = toolDependencyGraphPort.emptyReadyGraph(
                        catalogEntry.serverIdPrefix(), saved.tools());
            } else {
                graph = toolDependencyGraphPort.generate(
                        catalogEntry.serverIdPrefix(), saved.tools());
            }
            registryPort.updateDependencyGraph(saved.id(), graph);
        } catch (Exception exception) {
            // Defensive: generate() should not throw, but a storage hiccup must not fail connect.
            log.warn("[MCP] Dependency-graph generation/storage failed connectionId={}: {}",
                    saved.id(), exception.getMessage());
        }
    }

    private static boolean shouldSkipDependencyGraph(McpCatalogEntry catalogEntry) {
        McpToolSelectionConfig toolSelection = catalogEntry.toolSelection();
        return toolSelection != null && toolSelection.skipDependencyGraph();
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
