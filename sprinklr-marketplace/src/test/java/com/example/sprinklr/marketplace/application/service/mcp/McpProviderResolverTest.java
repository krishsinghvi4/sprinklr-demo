package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpAuthConfig;
import com.example.sprinklr.marketplace.domain.model.McpAuthKind;
import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.McpOAuthCatalogConfig;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.AtlassianOAuthAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.BasicEmailTokenAuthStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.McpAuthStrategyRegistry;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProviderResolverTest {

    private McpCatalogLoader catalogLoader;
    private McpProviderResolver resolver;

    @BeforeEach
    void setUp() {
        catalogLoader = mock(McpCatalogLoader.class);
        McpAuthStrategyRegistry authStrategyRegistry = new McpAuthStrategyRegistry(List.of(
                new AtlassianOAuthAuthStrategy(),
                new BasicEmailTokenAuthStrategy()
        ));
        DefaultCatalogMcpProvider defaultProvider = new DefaultCatalogMcpProvider(authStrategyRegistry);
        AtlassianMcpProvider atlassianProvider = new AtlassianMcpProvider(authStrategyRegistry);
        resolver = new McpProviderResolver(
                List.of(atlassianProvider, defaultProvider),
                defaultProvider,
                catalogLoader
        );
    }

    @Test
    void resolvesAtlassianProviderForJiraCatalogEntry() {
        McpCatalogEntry jira = McpCatalogTestFixtures.jiraEntry();
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(jira));

        McpProvider provider = resolver.require("atlassian-jira");
        assertTrue(provider instanceof AtlassianMcpProvider);
        assertEquals("atlassian", provider.providerKey(jira));
    }

    @Test
    void resolvesDefaultProviderForCredentialCatalogEntry() {
        McpCatalogEntry gitlab = credentialEntry();
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(gitlab));

        McpProvider provider = resolver.require("gitlab-mcp");
        assertSame(DefaultCatalogMcpProvider.class, provider.getClass());
    }

    private static McpCatalogEntry credentialEntry() {
        return new McpCatalogEntry(
                "gitlab-mcp",
                "GitLab",
                "desc",
                "https://gitlab.example.com/mcp",
                "gitlab",
                "GITLAB_PRIVATE_TOKEN",
                new McpAuthConfig(McpAuthKind.CREDENTIALS, null),
                McpConnectMethod.CREDENTIAL_FORM,
                List.of()
        );
    }
}
