package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.oauth;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpDcrClientDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpDcrClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpDcrRegistrationServiceTest {

    @Mock
    private WebClient webClient;
    @Mock
    private McpDcrClientRepository dcrClientRepository;
    @Mock
    private McpOAuthConfigResolver oauthConfigResolver;

    private McpDcrRegistrationService service;
    private McpCatalogEntry jiraEntry;

    @BeforeEach
    void setUp() {
        service = new McpDcrRegistrationService(webClient, dcrClientRepository, oauthConfigResolver);
        jiraEntry = McpCatalogTestFixtures.jiraEntry();
    }

    @Test
    void getOrRegisterClientMigratesLegacyDocumentWithoutProviderKey() {
        String redirectUri = "http://localhost:5173/oauth/callback";
        McpOAuthCatalogConfig oauth = jiraEntry.authConfig().oauth();
        McpDcrClientDocument legacy = new McpDcrClientDocument(
                "legacy-id",
                null,
                redirectUri,
                "client-id",
                "client-secret",
                Instant.parse("2026-06-16T13:26:35.400Z")
        );

        when(oauthConfigResolver.resolve(jiraEntry)).thenReturn(oauth);
        when(oauthConfigResolver.redirectUri()).thenReturn(redirectUri);
        when(dcrClientRepository.findByProviderKeyAndRedirectUri("atlassian", redirectUri))
                .thenReturn(Optional.empty());
        when(dcrClientRepository.findByRedirectUri(redirectUri)).thenReturn(Optional.of(legacy));
        when(dcrClientRepository.save(any(McpDcrClientDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        McpDcrClientDocument result = service.getOrRegisterClient(jiraEntry);

        assertEquals("atlassian", result.providerKey());
        assertEquals("client-id", result.clientId());

        ArgumentCaptor<McpDcrClientDocument> captor = ArgumentCaptor.forClass(McpDcrClientDocument.class);
        verify(dcrClientRepository).save(captor.capture());
        assertEquals("atlassian", captor.getValue().providerKey());
        assertEquals("legacy-id", captor.getValue().id());
    }

    @Test
    void getOrRegisterClientReturnsExistingProviderKeyedDocument() {
        String redirectUri = "http://localhost:5173/oauth/callback";
        McpOAuthCatalogConfig oauth = jiraEntry.authConfig().oauth();
        McpDcrClientDocument existing = new McpDcrClientDocument(
                "existing-id",
                "atlassian",
                redirectUri,
                "client-id",
                "client-secret",
                Instant.now()
        );

        when(oauthConfigResolver.resolve(jiraEntry)).thenReturn(oauth);
        when(oauthConfigResolver.redirectUri()).thenReturn(redirectUri);
        when(dcrClientRepository.findByProviderKeyAndRedirectUri("atlassian", redirectUri))
                .thenReturn(Optional.of(existing));

        McpDcrClientDocument result = service.getOrRegisterClient(jiraEntry);

        assertEquals(existing, result);
        verify(dcrClientRepository, never()).findByRedirectUri(any());
        verify(dcrClientRepository, never()).save(any());
    }
}
