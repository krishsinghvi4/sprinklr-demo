package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCredentialHeaderMode;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals("OAUTH", jira.authType());
        assertNotNull(jira.toolSelection());
        assertEquals(3, jira.toolSelection().continuationNeverSatisfyTools().size());

        var gitlab = loader.findById("gitlab-mcp").orElseThrow();
        assertEquals(McpConnectMethod.CREDENTIAL_FORM, gitlab.connectMethod());
        assertTrue(gitlab.authConfig().isCredentials());
        assertEquals(McpCredentialHeaderMode.PRIVATE_TOKEN, gitlab.authConfig().credentials().headerMode());
        assertEquals(1, gitlab.credentialFields().size());
        assertEquals("http://127.0.0.1:3333/mcp", gitlab.endpointUrl());
        assertNotNull(gitlab.connectProbe());
        assertEquals("list_namespaces", gitlab.connectProbe().tool());

        var red = loader.findById("red-mcp").orElseThrow();
        assertEquals(McpConnectMethod.CREDENTIAL_FORM, red.connectMethod());
        assertEquals(McpCredentialHeaderMode.BEARER, red.authConfig().credentials().headerMode());
        assertEquals("http://127.0.0.1:3344/mcp", red.endpointUrl());
        assertEquals("red_ping", red.connectProbe().tool());
        assertEquals("classpath:llm/mcp-skills/red.md", red.llmSkillPath());
        assertNotNull(red.toolSelection());
        assertEquals(2, red.toolSelection().staticDependencyGraph().size());
        assertEquals(
                List.of("red_sample_mongo_query"),
                red.toolSelection().staticDependencyGraph().get("red_execute_mongo_query"));
        assertEquals(
                List.of("red_sample_elasticsearch_query"),
                red.toolSelection().staticDependencyGraph().get("red_execute_elastic_search_query"));
        assertNull(gitlab.toolSelection());

        assertEquals("classpath:llm/mcp-skills/jira.md", jira.llmSkillPath());
        assertEquals("classpath:llm/mcp-skills/gitlab.md", gitlab.llmSkillPath());

        var teams = loader.findById("teams-messages").orElseThrow();
        assertEquals(McpConnectMethod.LOCAL_ONLY, teams.connectMethod());
        assertEquals("teams", teams.serverIdPrefix());
        assertEquals("LOCAL_ONLY", teams.endpointUrl());
        assertEquals("classpath:llm/mcp-skills/teams.md", teams.llmSkillPath());
        assertTrue(teams.credentialFields().isEmpty());
        assertNull(teams.connectProbe());

        assertEquals(4, loader.getAll().size());
    }
}
