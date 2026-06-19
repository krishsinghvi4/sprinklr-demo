package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCatalogLoaderTest {

    @Test
    void loadsExtendedCatalogWithConnectMethodAndAuthBlocks() throws Exception {
        McpProperties properties = new McpProperties();
        properties.setCatalogPath("classpath:mcp/mcp-catalog.json");
        McpCatalogLoader loader = new McpCatalogLoader(properties, new DefaultResourceLoader());

        var jira = loader.findById("atlassian-jira").orElseThrow();
        assertEquals(McpConnectMethod.OAUTH_REDIRECT, jira.connectMethod());
        assertTrue(jira.authConfig().isOAuth());
        assertEquals("atlassian", jira.authConfig().oauth().providerKey());

        var gitlab = loader.findById("gitlab-mcp").orElseThrow();
        assertEquals(McpConnectMethod.CREDENTIAL_FORM, gitlab.connectMethod());
        assertTrue(gitlab.authConfig().isCredentials());
        assertEquals(1, gitlab.credentialFields().size());
        assertEquals("http://127.0.0.1:3333/mcp", gitlab.endpointUrl());
        assertEquals("classpath:llm/mcp-skills/jira.txt", jira.llmSkillPath());
        assertEquals("classpath:llm/mcp-skills/gitlab.txt", gitlab.llmSkillPath());
    }
}
