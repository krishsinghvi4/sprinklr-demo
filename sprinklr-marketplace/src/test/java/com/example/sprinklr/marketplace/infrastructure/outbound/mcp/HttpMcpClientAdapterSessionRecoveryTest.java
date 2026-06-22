package com.example.sprinklr.marketplace.infrastructure.outbound.mcp;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.application.service.mcp.McpOAuthTokenRefreshService;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.AtlassianJiraToolArgumentNormalizer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueTypeCreateRequirementsCache;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueTypeFieldShapeCache;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.GitLabPrivateTokenAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMcpClientAdapterSessionRecoveryTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void retriesGitLabToolCallAfterEconnresetByReinitializingSession() throws Exception {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        CredentialVaultPort credentialVault = mock(CredentialVaultPort.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpAuthStrategyRegistry authStrategyRegistry = mock(McpAuthStrategyRegistry.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpOAuthTokenRefreshService oauthTokenRefreshService = mock(McpOAuthTokenRefreshService.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);
        AtlassianJiraToolArgumentNormalizer argumentNormalizer =
                new AtlassianJiraToolArgumentNormalizer(new JiraIssueTypeFieldShapeCache());

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
                null
        );

        McpCatalogEntry catalogEntry = McpCatalogTestFixtures.gitlabEntry();
        Map<String, String> credentials = Map.of("apiToken", "pat-token");

        when(repository.findById("conn-gitlab")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-gitlab")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(credentialVault.decrypt("encrypted")).thenReturn(credentials);
        when(oauthTokenRefreshService.refreshIfNeeded(eq(connection), eq(catalogEntry), eq(credentials)))
                .thenReturn(credentials);
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(catalogEntry));
        when(authStrategyRegistry.require(GitLabPrivateTokenAuthStrategy.AUTH_TYPE))
                .thenReturn(new GitLabPrivateTokenAuthStrategy(new com.example.sprinklr.marketplace.infrastructure.config.McpProperties()));
        when(mcpClient.initialize(anyString(), anyMap()))
                .thenReturn(new StreamableHttpMcpClient.McpSession("fresh-session", "2025-03-26"));
        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new McpDiscoveryException(
                        "Could not reach MCP server — try again later",
                        "MCP JSON-RPC error for tools/call: request failed, reason: read ECONNRESET"))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"[]\"}]}"));

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                credentialVault,
                catalogLoader,
                authStrategyRegistry,
                mcpClient,
                oauthTokenRefreshService,
                circuitBreakerFactory,
                argumentNormalizer,
                new JiraIssueTypeFieldShapeCache(),
                new JiraIssueTypeCreateRequirementsCache()
        );

        var result = adapter.invoke(new McpInvocation(
                "conn-gitlab", "list_merge_requests", "{\"per_page\":1}", "call-1"));

        assertTrue(result.success());
        verify(mcpClient).initialize(catalogEntry.endpointUrl(), Map.of("Private-Token", "pat-token"));
        verify(mcpClient, times(2)).callTool(
                eq(catalogEntry.endpointUrl()),
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
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError("MCP HTTP error status=404"));
        assertTrue(HttpMcpClientAdapter.isRecoverableTransportError(
                "MCP request failed method=tools/call status=400 body={\"message\":\"Bad Request: Server not initialized\"}"));
    }

    @Test
    void retriesGitLabToolCallAfterServerNotInitializedByReinitializingSession() throws Exception {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        CredentialVaultPort credentialVault = mock(CredentialVaultPort.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpAuthStrategyRegistry authStrategyRegistry = mock(McpAuthStrategyRegistry.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpOAuthTokenRefreshService oauthTokenRefreshService = mock(McpOAuthTokenRefreshService.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);
        AtlassianJiraToolArgumentNormalizer argumentNormalizer =
                new AtlassianJiraToolArgumentNormalizer(new JiraIssueTypeFieldShapeCache());

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
                null
        );

        McpCatalogEntry catalogEntry = McpCatalogTestFixtures.gitlabEntry();
        Map<String, String> credentials = Map.of("apiToken", "pat-token");

        when(repository.findById("conn-gitlab")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-gitlab")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(credentialVault.decrypt("encrypted")).thenReturn(credentials);
        when(oauthTokenRefreshService.refreshIfNeeded(eq(connection), eq(catalogEntry), eq(credentials)))
                .thenReturn(credentials);
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(catalogEntry));
        when(authStrategyRegistry.require(GitLabPrivateTokenAuthStrategy.AUTH_TYPE))
                .thenReturn(new GitLabPrivateTokenAuthStrategy(new com.example.sprinklr.marketplace.infrastructure.config.McpProperties()));
        when(mcpClient.initialize(anyString(), anyMap()))
                .thenReturn(new StreamableHttpMcpClient.McpSession("fresh-session", "2025-03-26"));
        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new McpConnectionException(
                        "Could not reach MCP server — try again later",
                        "MCP request failed method=tools/call status=400 body={\"error\":{\"message\":\"Bad Request: Server not initialized\"}}"))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":[{\"type\":\"text\",\"text\":\"[]\"}]}"));

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                credentialVault,
                catalogLoader,
                authStrategyRegistry,
                mcpClient,
                oauthTokenRefreshService,
                circuitBreakerFactory,
                argumentNormalizer,
                new JiraIssueTypeFieldShapeCache(),
                new JiraIssueTypeCreateRequirementsCache()
        );

        var result = adapter.invoke(new McpInvocation(
                "conn-gitlab", "list_merge_requests", "{\"per_page\":1}", "call-1"));

        assertTrue(result.success());
        verify(mcpClient).initialize(catalogEntry.endpointUrl(), Map.of("Private-Token", "pat-token"));
        verify(mcpClient, times(2)).callTool(
                eq(catalogEntry.endpointUrl()),
                anyMap(),
                anyString(),
                anyString(),
                eq("list_merge_requests"),
                anyString()
        );
    }
}
