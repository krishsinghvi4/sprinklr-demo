package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth.CatalogAuthHeaderBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpProviderResolverTest {

    private McpCatalogLoader catalogLoader;
    private McpProviderResolver resolver;

    @BeforeEach
    void setUp() {
        catalogLoader = mock(McpCatalogLoader.class);
        DefaultCatalogMcpProvider defaultProvider =
                new DefaultCatalogMcpProvider(new CatalogAuthHeaderBuilder());
        resolver = new McpProviderResolver(
                List.of(defaultProvider),
                defaultProvider,
                catalogLoader
        );
    }

    @Test
    void resolvesDefaultProviderForOAuthCatalogEntry() {
        var jira = McpCatalogTestFixtures.jiraEntry();
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(jira));

        McpProvider provider = resolver.require("atlassian-jira");
        assertSame(DefaultCatalogMcpProvider.class, provider.getClass());
        assertEquals("atlassian", provider.providerKey(jira));
    }

    @Test
    void resolvesDefaultProviderForCredentialCatalogEntry() {
        var gitlab = McpCatalogTestFixtures.gitlabEntry();
        when(catalogLoader.findById("gitlab-mcp")).thenReturn(Optional.of(gitlab));

        McpProvider provider = resolver.require("gitlab-mcp");
        assertSame(DefaultCatalogMcpProvider.class, provider.getClass());
    }
}
