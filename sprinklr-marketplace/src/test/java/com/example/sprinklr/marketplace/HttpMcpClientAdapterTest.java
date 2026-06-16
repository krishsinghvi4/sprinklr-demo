package com.example.sprinklr.marketplace;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.HttpMcpClientAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpCircuitBreakerFactory;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.AtlassianOAuthToken;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMcpClientAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void refreshesExpiredOauthTokenBeforeToolCall() throws Exception {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        CredentialVaultPort credentialVault = mock(CredentialVaultPort.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpAuthStrategyRegistry authStrategyRegistry = mock(McpAuthStrategyRegistry.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpOAuthClient oauthClient = mock(McpOAuthClient.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);

        McpConnectionDocument connection = new McpConnectionDocument(
                "conn-1",
                "user-1",
                "atlassian-jira",
                "jira",
                "encrypted",
                "session-1",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null
        );

        when(repository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-1")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(credentialVault.decrypt("encrypted")).thenReturn(Map.of(
                AtlassianOAuthToken.ACCESS_TOKEN_KEY, "expired-token",
                AtlassianOAuthToken.REFRESH_TOKEN_KEY, "refresh-token",
                AtlassianOAuthToken.EXPIRES_AT_KEY, Long.toString(Instant.now().minusSeconds(10).getEpochSecond())
        ));
        when(credentialVault.encrypt(anyMap())).thenReturn("encrypted-updated");
        when(oauthClient.refreshAccessToken("refresh-token")).thenReturn(new AtlassianOAuthToken(
                "new-token",
                "new-refresh",
                Instant.now().plusSeconds(3600).getEpochSecond(),
                "scope",
                "bearer"
        ));

        McpCatalogEntry catalogEntry = new McpCatalogEntry(
                "atlassian-jira",
                "Jira",
                "desc",
                "https://mcp.atlassian.com/v1/mcp/authv2",
                "jira",
                "OAUTH_ATLASSIAN",
                List.of()
        );
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(catalogEntry));
        when(authStrategyRegistry.require("OAUTH_ATLASSIAN")).thenReturn(new AtlassianOAuthAuthStrategy());
        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":\"ok\"}"));

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                credentialVault,
                catalogLoader,
                authStrategyRegistry,
                mcpClient,
                oauthClient,
                circuitBreakerFactory
        );

        var result = adapter.invoke(new McpInvocation("conn-1", "jira.search", "{}", "call-1"));
        assertTrue(result.success());
        verify(oauthClient).refreshAccessToken("refresh-token");
        verify(repository).save(any(McpConnectionDocument.class));
    }
}
