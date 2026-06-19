package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.config.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.config.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpSkillPromptAssemblerTest {

    private McpSkillPromptAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setStubEnabled(true);
        llmProperties.setSystemPromptPath("classpath:llm/system-prompt.txt");
        LlmSystemPromptLoader systemPromptLoader = new LlmSystemPromptLoader(llmProperties, new DefaultResourceLoader());

        McpProperties properties = new McpProperties();
        properties.setCatalogPath("classpath:mcp/mcp-catalog.json");
        McpCatalogLoader catalogLoader = new McpCatalogLoader(properties, new DefaultResourceLoader());
        McpSkillPromptLoader skillPromptLoader = new McpSkillPromptLoader(catalogLoader, new DefaultResourceLoader());
        assembler = new McpSkillPromptAssembler(systemPromptLoader, skillPromptLoader);
    }

    @Test
    void returnsBasePromptWhenNoActiveTools() {
        String result = assembler.assemble(List.of());
        assertTrue(result.contains("Sprinklr Developer Marketplace AI Assistant"));
        assertFalse(result.contains("Connected MCP guidance"));
    }

    @Test
    void appendsGitLabSkillSectionForConnectedGitLabTools() {
        List<McpTool> tools = List.of(
                gitlabTool("gitlab.get_commit"),
                gitlabTool("gitlab.list_commits")
        );

        String result = assembler.assemble(tools);

        assertTrue(result.contains("Sprinklr Developer Marketplace AI Assistant"));
        assertTrue(result.contains("## Connected MCP guidance"));
        assertTrue(result.contains("### GitLab"));
        assertTrue(result.contains("project_id"));
        assertFalse(result.contains("### Jira"));
    }

    @Test
    void includesMultipleSkillSectionsWhenMultiplePrefixesConnected() {
        List<McpTool> tools = List.of(
                gitlabTool("gitlab.get_commit"),
                jiraTool("jira.getJiraIssue")
        );

        String result = assembler.assemble(tools);

        assertTrue(result.contains("### Jira"));
        assertTrue(result.contains("searchJiraIssuesUsingJql"));
        assertTrue(result.contains("### GitLab"));
        assertTrue(result.contains("get_branch_diffs"));
    }

    private static McpTool gitlabTool(String name) {
        return new McpTool(name, "desc", "conn-gitlab", "{\"type\":\"object\"}");
    }

    private static McpTool jiraTool(String name) {
        return new McpTool(name, "desc", "conn-jira", "{\"type\":\"object\"}");
    }
}
