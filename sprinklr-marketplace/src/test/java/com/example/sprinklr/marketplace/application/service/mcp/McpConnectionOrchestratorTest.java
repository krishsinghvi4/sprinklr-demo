package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpDiscoveryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolDependencyGraphPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.connect.CompositeMcpConnectValidationAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolCatalogMerger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpConnectionOrchestratorTest {

    @Mock
    private McpCatalogLoader catalogLoader;
    @Mock
    private McpRegistryPort registryPort;
    @Mock
    private McpDiscoveryPort discoveryPort;
    @Mock
    private CredentialVaultPort credentialVault;
    @Mock
    private McpProviderResolver providerResolver;
    @Mock
    private CompositeMcpConnectValidationAdapter connectValidationAdapter;
    @Mock
    private ToolDependencyGraphPort toolDependencyGraphPort;
    @Mock
    private McpProvider provider;
    @Mock
    private McpLocalToolCatalogMerger localToolCatalogMerger;

    private final McpProperties mcpProperties = new McpProperties();

    private McpConnectionOrchestrator orchestrator;

    private final List<McpTool> discoveredTools = List.of(
            new McpTool("red.red_ping", "Ping RED", "connection-id", "{}"),
            new McpTool("red.red_query", "Run query", "connection-id", "{}")
    );

    @BeforeEach
    void setUp() {
        mcpProperties.getToolSelection().setGenerateGraphOnConnect(true);
        orchestrator = new McpConnectionOrchestrator(
                catalogLoader,
                registryPort,
                discoveryPort,
                credentialVault,
                providerResolver,
                connectValidationAdapter,
                toolDependencyGraphPort,
                mcpProperties,
                localToolCatalogMerger
        );
    }

    @Test
    void storesStaticDependencyGraphFromCatalog() {
        List<McpTool> redTools = List.of(
                new McpTool("red.red_sample_mongo_query", "Sample mongo", "connection-id", "{}"),
                new McpTool("red.red_execute_mongo_query", "Execute mongo", "connection-id", "{}"),
                new McpTool("red.red_ping", "Ping RED", "connection-id", "{}")
        );
        when(catalogLoader.findById("red-mcp")).thenReturn(Optional.of(McpCatalogTestFixtures.redEntry()));
        when(registryPort.findByUserIdAndCatalogServerId("user-1", "red-mcp")).thenReturn(Optional.empty());
        when(providerResolver.resolve(McpCatalogTestFixtures.redEntry())).thenReturn(provider);
        when(provider.buildAuthHeaders(any(), any())).thenReturn(Map.of("Authorization", "Bearer token"));
        when(discoveryPort.discover(any(), any(), any(), any()))
                .thenReturn(new McpDiscoveryPort.McpDiscoveryResult("session-1", "2024-11-05", redTools));
        when(localToolCatalogMerger.merge(any(), any(), eq(redTools))).thenReturn(redTools);
        when(credentialVault.encrypt(any())).thenReturn("encrypted");
        when(registryPort.saveConnection(any(), any(), eq("red"))).thenAnswer(invocation -> invocation.getArgument(0));
        when(toolDependencyGraphPort.emptyReadyGraph("red", redTools)).thenReturn(new ToolDependencyGraph(
                "red", Map.of(), "fingerprint", Instant.now(), DependencyGraphStatus.READY));

        orchestrator.connectWithCredentials("user-1", "red-mcp", Map.of("apiToken", "token"));

        verify(toolDependencyGraphPort, never()).generate(any(), any());
        verify(toolDependencyGraphPort).emptyReadyGraph("red", redTools);

        ArgumentCaptor<ToolDependencyGraph> graphCaptor = ArgumentCaptor.forClass(ToolDependencyGraph.class);
        verify(registryPort).updateDependencyGraph(any(), graphCaptor.capture());
        ToolDependencyGraph storedGraph = graphCaptor.getValue();
        assertEquals(DependencyGraphStatus.READY, storedGraph.status());
        assertEquals(
                List.of("red.red_sample_mongo_query"),
                storedGraph.edges().get("red.red_execute_mongo_query"));
    }

    @Test
    void generatesLlmDependencyGraphWhenNoStaticGraphConfigured() {
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(McpCatalogTestFixtures.gitlabEntry()));
        when(registryPort.findByUserIdAndCatalogServerId("user-1", "gitlab-mcp")).thenReturn(Optional.empty());
        when(providerResolver.resolve(McpCatalogTestFixtures.gitlabEntry())).thenReturn(provider);
        when(provider.buildAuthHeaders(any(), any())).thenReturn(Map.of("PRIVATE-TOKEN", "token"));
        when(discoveryPort.discover(any(), any(), any(), any()))
                .thenReturn(new McpDiscoveryPort.McpDiscoveryResult("session-1", "2024-11-05", discoveredTools));
        when(localToolCatalogMerger.merge(any(), any(), eq(discoveredTools))).thenReturn(discoveredTools);
        when(credentialVault.encrypt(any())).thenReturn("encrypted");
        when(registryPort.saveConnection(any(), any(), eq("gitlab"))).thenAnswer(invocation -> invocation.getArgument(0));

        ToolDependencyGraph generatedGraph = new ToolDependencyGraph(
                "gitlab",
                Map.of("gitlab.create_mr", List.of("gitlab.search")),
                "fingerprint",
                Instant.now(),
                DependencyGraphStatus.READY);
        when(toolDependencyGraphPort.generate("gitlab", discoveredTools)).thenReturn(generatedGraph);

        orchestrator.connectWithCredentials("user-1", "gitlab-mcp", Map.of("apiToken", "token"));

        verify(toolDependencyGraphPort).generate("gitlab", discoveredTools);
        verify(toolDependencyGraphPort, never()).emptyReadyGraph(any(), any());

        ArgumentCaptor<ToolDependencyGraph> graphCaptor = ArgumentCaptor.forClass(ToolDependencyGraph.class);
        verify(registryPort).updateDependencyGraph(any(), graphCaptor.capture());
        assertTrue(graphCaptor.getValue().edges().containsKey("gitlab.create_mr"));
    }
}
