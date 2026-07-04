package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.application.service.mcp.McpOAuthTokenRefreshService;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.AtlassianJiraToolArgumentNormalizer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.CatalogAuthHeaderBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.CompositeMcpToolArgumentNormalizer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.CompositeMcpToolResultPostProcessor;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.CompositeMcpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationPreparer;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMcpClientAdapterSessionRecoveryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void retriesGitLabToolCallAfterEconnresetByReinitializingSession() throws Exception {
        var deps = gitLabAdapterDependencies();
        when(deps.mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new McpDiscoveryException(
                        "Could not reach MCP server — try again later",
                        "MCP JSON-RPC error for tools/call: request failed, reason: read ECONNRESET"))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"[]\"}]}"));

        var result = deps.adapter.invoke(new McpInvocation(
                "conn-gitlab", "list_merge_requests", "{\"per_page\":1}", "call-1"));

        assertTrue(result.success());
        verify(deps.mcpClient).initialize(deps.catalogEntry.endpointUrl(), Map.of("Private-Token", "pat-token"));
        verify(deps.mcpClient, times(2)).callTool(
                eq(deps.catalogEntry.endpointUrl()),
                anyMap(),
                anyString(),
                anyString(),
                eq("list_merge_requests"),
                anyString()
        );
        verify(deps.oauthTokenRefreshService, never())
                .refreshIfNeeded(eq(deps.connection), eq(deps.catalogEntry), anyMap());
    }

    @Test
    void retriesGitLabToolCallAfterSocketHangUpOnThirdAttempt() throws Exception {
        var deps = gitLabAdapterDependencies();
        when(deps.mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new McpDiscoveryException(
                        "Could not reach MCP server — try again later",
                        "MCP JSON-RPC error for tools/call: request failed, reason: socket hang up"))
                .thenThrow(new McpDiscoveryException(
                        "Could not reach MCP server — try again later",
                        "MCP JSON-RPC error for tools/call: request failed, reason: socket hang up"))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"[]\"}]}"));

        var result = deps.adapter.invoke(new McpInvocation(
                "conn-gitlab", "list_merge_requests", "{\"per_page\":1}", "call-1"));

        assertTrue(result.success());
        verify(deps.mcpClient, times(3)).callTool(
                eq(deps.catalogEntry.endpointUrl()),
                anyMap(),
                anyString(),
                anyString(),
                eq("list_merge_requests"),
                anyString()
        );
    }

    @Test
    void detectsRecoverableTransportErrors() {
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError(
                "MCP JSON-RPC error for tools/call: request failed, reason: read ECONNRESET"));
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError(
                "MCP JSON-RPC error for tools/call: request failed, reason: socket hang up"));
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError("MCP HTTP error status=404"));
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError(
                "MCP request failed method=tools/call status=400 body={\"message\":\"Bad Request: Server not initialized\"}"));
    }

    @Test
    void retriesGitLabToolCallAfterServerNotInitializedByReinitializingSession() throws Exception {
        var deps = gitLabAdapterDependencies();
        when(deps.mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new McpConnectionException(
                        "Could not reach MCP server — try again later",
                        "MCP request failed method=tools/call status=400 body={\"error\":{\"message\":\"Bad Request: Server not initialized\"}}"))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"[]\"}]}"));

        var result = deps.adapter.invoke(new McpInvocation(
                "conn-gitlab", "list_merge_requests", "{\"per_page\":1}", "call-1"));

        assertTrue(result.success());
        verify(deps.mcpClient).initialize(deps.catalogEntry.endpointUrl(), Map.of("Private-Token", "pat-token"));
        verify(deps.mcpClient, times(2)).callTool(
                eq(deps.catalogEntry.endpointUrl()),
                anyMap(),
                anyString(),
                anyString(),
                eq("list_merge_requests"),
                anyString()
        );
    }

    private GitLabAdapterDeps gitLabAdapterDependencies() {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        CredentialVaultPort credentialVault = mock(CredentialVaultPort.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpOAuthTokenRefreshService oauthTokenRefreshService = mock(McpOAuthTokenRefreshService.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);

        McpConnectionDocument connection = new McpConnectionDocument(
                "conn-gitlab",
                "user-1",
                "gitlab-mcp",
                "gitlab",
                "encrypted",
                "stale-session",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null,
                null,
                null,
                null
        );

        var catalogEntry = McpCatalogTestFixtures.gitlabEntry();
        Map<String, String> credentials = Map.of("apiToken", "pat-token");

        when(repository.findById("conn-gitlab")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-gitlab")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(credentialVault.decrypt("encrypted")).thenReturn(credentials);
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(catalogEntry));
        when(mcpClient.initialize(anyString(), anyMap()))
                .thenReturn(new StreamableHttpMcpClient.McpSession("fresh-session", "2025-03-26"));

        McpInvocationPreparer invocationPreparer = new McpInvocationPreparer(
                repository,
                credentialVault,
                catalogLoader,
                new CatalogAuthHeaderBuilder(),
                mcpClient,
                oauthTokenRefreshService,
                new CompositeMcpToolArgumentNormalizer(List.of(new AtlassianJiraToolArgumentNormalizer()))
        );

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                mcpClient,
                circuitBreakerFactory,
                invocationPreparer,
                new CompositeMcpToolResultPostProcessor(List.of()),
                new CompositeMcpLocalToolExtension(List.of())
        );

        return new GitLabAdapterDeps(adapter, mcpClient, oauthTokenRefreshService, connection, catalogEntry);
    }

    private record GitLabAdapterDeps(
            HttpMcpClientAdapter adapter,
            StreamableHttpMcpClient mcpClient,
            McpOAuthTokenRefreshService oauthTokenRefreshService,
            McpConnectionDocument connection,
            com.example.sprinklr.marketplace.domain.model.McpCatalogEntry catalogEntry
    ) {
    }
}
