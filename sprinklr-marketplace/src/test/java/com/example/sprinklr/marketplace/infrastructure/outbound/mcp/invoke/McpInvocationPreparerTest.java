package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.application.service.mcp.McpOAuthTokenRefreshService;
import com.example.sprinklr.marketplace.domain.port.outbound.CredentialVaultPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.CatalogAuthHeaderBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpInvocationPreparerTest {

    @Test
    void refreshesOAuthCredentialsForOAuthCatalogEntry() {
        var repository = mock(McpConnectionRepository.class);
        var credentialVault = mock(CredentialVaultPort.class);
        var catalogLoader = mock(McpCatalogLoader.class);
        var mcpClient = mock(StreamableHttpMcpClient.class);
        var oauthTokenRefreshService = mock(McpOAuthTokenRefreshService.class);

        var connection = new McpConnectionDocument(
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
                null,
                null,
                null
        );
        var catalogEntry = McpCatalogTestFixtures.jiraEntry();
        Map<String, String> expired = Map.of("accessToken", "expired");
        Map<String, String> refreshed = Map.of("accessToken", "fresh");

        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(catalogEntry));
        when(credentialVault.decrypt("encrypted")).thenReturn(expired);
        when(oauthTokenRefreshService.refreshIfNeeded(connection, catalogEntry, expired)).thenReturn(refreshed);

        var preparer = new McpInvocationPreparer(
                repository,
                credentialVault,
                catalogLoader,
                new CatalogAuthHeaderBuilder(),
                mcpClient,
                oauthTokenRefreshService,
                new CompositeMcpToolArgumentNormalizer(List.of())
        );

        var prepared = preparer.prepare(connection, "jira.search", "{}");

        verify(oauthTokenRefreshService).refreshIfNeeded(connection, catalogEntry, expired);
        assertEquals("Bearer fresh", prepared.authHeaders().get("Authorization"));
    }

    @Test
    void skipsOAuthRefreshForCredentialCatalogEntry() {
        var repository = mock(McpConnectionRepository.class);
        var credentialVault = mock(CredentialVaultPort.class);
        var catalogLoader = mock(McpCatalogLoader.class);
        var mcpClient = mock(StreamableHttpMcpClient.class);
        var oauthTokenRefreshService = mock(McpOAuthTokenRefreshService.class);

        var connection = new McpConnectionDocument(
                "conn-red",
                "user-1",
                "red-mcp",
                "red",
                "encrypted",
                "session-1",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null,
                null,
                null
        );
        var catalogEntry = McpCatalogTestFixtures.redEntry();
        Map<String, String> credentials = Map.of("apiToken", "red-token");

        when(catalogLoader.findById("red-mcp")).thenReturn(Optional.of(catalogEntry));
        when(credentialVault.decrypt("encrypted")).thenReturn(credentials);

        var preparer = new McpInvocationPreparer(
                repository,
                credentialVault,
                catalogLoader,
                new CatalogAuthHeaderBuilder(),
                mcpClient,
                oauthTokenRefreshService,
                new CompositeMcpToolArgumentNormalizer(List.of())
        );

        var prepared = preparer.prepare(connection, "red_ping", "{}");

        verify(oauthTokenRefreshService, never()).refreshIfNeeded(eq(connection), eq(catalogEntry), eq(credentials));
        assertEquals("Bearer red-token", prepared.authHeaders().get("Authorization"));
    }
}
