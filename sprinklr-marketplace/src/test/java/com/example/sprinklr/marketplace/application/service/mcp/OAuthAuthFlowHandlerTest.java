package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpOAuthException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthConfigResolver;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth.McpOAuthToken;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpOAuthStateDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpOAuthStateRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuthAuthFlowHandlerTest {

    @Test
    void handleCallbackDeletesStateEvenWhenTokenExchangeFails() {
        McpOAuthStateRepository stateRepository = mock(McpOAuthStateRepository.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpOAuthClient oauthClient = mock(McpOAuthClient.class);
        McpOAuthConfigResolver oauthConfigResolver = mock(McpOAuthConfigResolver.class);
        McpProviderResolver providerResolver = mock(McpProviderResolver.class);
        McpConnectionOrchestrator connectionOrchestrator = mock(McpConnectionOrchestrator.class);
        McpProperties properties = new McpProperties();

        var entry = McpCatalogTestFixtures.jiraEntry();
        McpOAuthStateDocument stateDoc = new McpOAuthStateDocument(
                "state-1",
                "user-1",
                "atlassian-jira",
                "atlassian",
                "verifier",
                Instant.now(),
                Instant.now().plusSeconds(600)
        );

        when(stateRepository.findById("state-1")).thenReturn(Optional.of(stateDoc));
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(entry));
        when(oauthClient.exchangeCodeForTokens(entry, "code-1", "verifier"))
                .thenThrow(new McpOAuthException("failed", "token exchange failed"));

        OAuthAuthFlowHandler handler = new OAuthAuthFlowHandler(
                stateRepository,
                catalogLoader,
                oauthClient,
                oauthConfigResolver,
                providerResolver,
                connectionOrchestrator,
                properties
        );

        assertThrows(McpOAuthException.class, () -> handler.handleCallback("state-1", "code-1"));
        verify(stateRepository).deleteById("state-1");
        verify(connectionOrchestrator, never()).connectWithCredentials(any(), any(), any());
    }

    @Test
    void startOAuthDeletesStaleStatesForUserAndServer() {
        McpOAuthStateRepository stateRepository = mock(McpOAuthStateRepository.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpOAuthClient oauthClient = mock(McpOAuthClient.class);
        McpOAuthConfigResolver oauthConfigResolver = mock(McpOAuthConfigResolver.class);
        McpProviderResolver providerResolver = mock(McpProviderResolver.class);
        McpConnectionOrchestrator connectionOrchestrator = mock(McpConnectionOrchestrator.class);
        McpProperties properties = new McpProperties();

        var entry = McpCatalogTestFixtures.jiraEntry();
        AtlassianMcpProvider atlassianProvider = mock(AtlassianMcpProvider.class);

        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(entry));
        when(providerResolver.resolve(entry)).thenReturn(atlassianProvider);
        when(atlassianProvider.providerKey(entry)).thenReturn("atlassian");
        when(oauthConfigResolver.redirectUri()).thenReturn("http://localhost:5173/oauth/callback");
        when(oauthClient.buildAuthorizationUrl(eq(entry), any(), any()))
                .thenReturn("https://auth.example/authorize");

        OAuthAuthFlowHandler handler = new OAuthAuthFlowHandler(
                stateRepository,
                catalogLoader,
                oauthClient,
                oauthConfigResolver,
                providerResolver,
                connectionOrchestrator,
                properties
        );

        handler.startOAuth("user-1", "atlassian-jira");
        verify(stateRepository).deleteByUserIdAndCatalogServerId("user-1", "atlassian-jira");
        verify(stateRepository).save(any(McpOAuthStateDocument.class));
    }

    @Test
    void handleCallbackCompletesConnectOnSuccess() {
        McpOAuthStateRepository stateRepository = mock(McpOAuthStateRepository.class);
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        McpOAuthClient oauthClient = mock(McpOAuthClient.class);
        McpOAuthConfigResolver oauthConfigResolver = mock(McpOAuthConfigResolver.class);
        McpProviderResolver providerResolver = mock(McpProviderResolver.class);
        McpConnectionOrchestrator connectionOrchestrator = mock(McpConnectionOrchestrator.class);
        McpProperties properties = new McpProperties();

        var entry = McpCatalogTestFixtures.jiraEntry();
        McpOAuthStateDocument stateDoc = new McpOAuthStateDocument(
                "state-1",
                "user-1",
                "atlassian-jira",
                "atlassian",
                "verifier",
                Instant.now(),
                Instant.now().plusSeconds(600)
        );
        McpOAuthToken token = new McpOAuthToken(
                "access",
                "refresh",
                Instant.now().plusSeconds(3600).getEpochSecond(),
                "scope",
                "bearer"
        );

        when(stateRepository.findById("state-1")).thenReturn(Optional.of(stateDoc));
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(entry));
        when(oauthClient.exchangeCodeForTokens(entry, "code-1", "verifier")).thenReturn(token);

        OAuthAuthFlowHandler handler = new OAuthAuthFlowHandler(
                stateRepository,
                catalogLoader,
                oauthClient,
                oauthConfigResolver,
                providerResolver,
                connectionOrchestrator,
                properties
        );

        handler.handleCallback("state-1", "code-1");
        verify(stateRepository).deleteById("state-1");
        verify(connectionOrchestrator).connectWithCredentials("user-1", "atlassian-jira", token.toCredentialMap());
    }
}
