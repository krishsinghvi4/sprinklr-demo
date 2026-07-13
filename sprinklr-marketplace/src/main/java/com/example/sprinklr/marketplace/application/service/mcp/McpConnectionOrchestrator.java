package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.MCP.McpToolSelectionConfig;
import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpDiscoveryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolDependencyGraphPort;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.MergedCatalogResolver;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect.CompositeMcpConnectValidationAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolCatalogMerger;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Shared connect orchestration: discovery handshake, credential encryption, registry persistence.
 * Used by both OAuth callback and credential-form connect flows.
 */
@Component
public class McpConnectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(McpConnectionOrchestrator.class);

    private final MergedCatalogResolver catalogResolver;
    private final McpRegistryPort registryPort;
    private final McpDiscoveryPort discoveryPort;
    private final CredentialVaultPort credentialVault;
    private final McpProviderResolver providerResolver;
    private final CompositeMcpConnectValidationAdapter connectValidationAdapter;
    private final ToolDependencyGraphPort toolDependencyGraphPort;
    private final McpProperties mcpProperties;
    private final McpLocalToolCatalogMerger localToolCatalogMerger;

    public McpConnectionOrchestrator(
            MergedCatalogResolver catalogResolver,
            McpRegistryPort registryPort,
            McpDiscoveryPort discoveryPort,
            CredentialVaultPort credentialVault,
            McpProviderResolver providerResolver,
            CompositeMcpConnectValidationAdapter connectValidationAdapter,
            ToolDependencyGraphPort toolDependencyGraphPort,
            McpProperties mcpProperties,
            McpLocalToolCatalogMerger localToolCatalogMerger
    ) {
        this.catalogResolver = catalogResolver;
        this.registryPort = registryPort;
        this.discoveryPort = discoveryPort;
        this.credentialVault = credentialVault;
        this.providerResolver = providerResolver;
        this.connectValidationAdapter = connectValidationAdapter;
        this.toolDependencyGraphPort = toolDependencyGraphPort;
        this.mcpProperties = mcpProperties;
        this.localToolCatalogMerger = localToolCatalogMerger;
    }

    public McpMarketplaceService.ConnectionView connectWithCredentials(
            String userId,
            String catalogServerId,
            Map<String, String> credentials
    ) {
        McpCatalogEntry catalogEntry = catalogResolver.findById(catalogServerId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MCP server: " + catalogServerId));

        McpProvider provider = providerResolver.resolve(catalogEntry);

        Optional<McpUserConnection> existing = registryPort.findByUserIdAndCatalogServerId(userId, catalogServerId);
        if (existing.isPresent()) {
            registryPort.delete(existing.get().id(), userId);
        }

        String connectionId = UUID.randomUUID().toString();

        if (catalogEntry.connectMethod() == McpConnectMethod.LOCAL_ONLY) {
            String encrypted = credentialVault.encrypt(credentials);
            List<McpTool> localTools = localToolCatalogMerger.merge(catalogEntry, connectionId, List.of());
            McpUserConnection connection = new McpUserConnection(
                    connectionId,
                    userId,
                    catalogServerId,
                    McpConnectionStatus.CONNECTED,
                    null,
                    null,
                    localTools,
                    Instant.now(),
                    null
            );
            McpUserConnection saved = registryPort.saveConnection(
                    connection,
                    encrypted,
                    catalogEntry.serverIdPrefix()
            );
            log.info("[MCP] Connected LOCAL_ONLY userId={} connectionId={} toolCount={}",
                    userId, connectionId, saved.tools().size());
            generateAndStoreDependencyGraph(saved, catalogEntry);
            return toConnectionView(saved, catalogEntry);
        }

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
        List<McpTool> mergedTools = localToolCatalogMerger.merge(
                catalogEntry,
                connectionId,
                discoveryResult.tools()
        );
        McpUserConnection connection = new McpUserConnection(
                connectionId,
                userId,
                catalogServerId,
                McpConnectionStatus.CONNECTED,
                discoveryResult.sessionId(),
                discoveryResult.protocolVersion(),
                mergedTools,
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
            if (hasStaticDependencyGraph(catalogEntry)) {
                log.info("[MCP] Using catalog static dependency graph for prefix={}",
                        catalogEntry.serverIdPrefix());
                graph = buildStaticDependencyGraph(catalogEntry, saved.tools());
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

    private static boolean hasStaticDependencyGraph(McpCatalogEntry catalogEntry) {
        McpToolSelectionConfig toolSelection = catalogEntry.toolSelection();
        return toolSelection != null && !toolSelection.staticDependencyGraph().isEmpty();
    }

    private ToolDependencyGraph buildStaticDependencyGraph(McpCatalogEntry catalogEntry, List<McpTool> tools) {
        String prefix = catalogEntry.serverIdPrefix();
        Set<String> availableToolNames = tools.stream().map(McpTool::name).collect(Collectors.toSet());
        Map<String, List<String>> qualifiedEdges = new LinkedHashMap<>();

        for (Map.Entry<String, List<String>> edge : catalogEntry.toolSelection().staticDependencyGraph().entrySet()) {
            String dependent = qualifyToolName(prefix, edge.getKey());
            if (!availableToolNames.contains(dependent)) {
                continue;
            }
            List<String> prerequisites = new ArrayList<>();
            for (String prerequisite : edge.getValue()) {
                String qualifiedPrerequisite = qualifyToolName(prefix, prerequisite);
                if (availableToolNames.contains(qualifiedPrerequisite)) {
                    prerequisites.add(qualifiedPrerequisite);
                }
            }
            if (!prerequisites.isEmpty()) {
                qualifiedEdges.put(dependent, List.copyOf(prerequisites));
            }
        }

        return new ToolDependencyGraph(
                prefix,
                Map.copyOf(qualifiedEdges),
                toolDependencyGraphPort.emptyReadyGraph(prefix, tools).toolsFingerprint(),
                Instant.now(),
                DependencyGraphStatus.READY);
    }

    private static String qualifyToolName(String prefix, String toolName) {
        if (toolName.contains(".")) {
            return toolName;
        }
        return prefix + "." + toolName;
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
                connection.connectedAt(),
                false
        );
    }
}
