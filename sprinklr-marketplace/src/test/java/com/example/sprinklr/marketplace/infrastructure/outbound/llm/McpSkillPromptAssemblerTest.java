package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
        CrossWorkflowSkillLoader crossWorkflowSkillLoader =
                new CrossWorkflowSkillLoader(catalogLoader, new DefaultResourceLoader());
        assembler = new McpSkillPromptAssembler(
                systemPromptLoader, skillPromptLoader, crossWorkflowSkillLoader, mock(UserMcpSkillProvider.class));
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

    @Test
    void includesCrossWorkflowSkillWhenJiraAndRedToolsScoped() {
        List<McpTool> tools = List.of(
                jiraTool("jira.getJiraIssue"),
                redTool("red.red_sample_elasticsearch_query")
        );

        String result = assembler.assemble(tools);

        assertTrue(result.contains("### Cross-server workflows"));
        assertTrue(result.contains("#### Jira → RED audit log investigation"));
        assertTrue(result.contains("Phase 1 — Jira context"));
        assertTrue(result.contains("### RED"));
    }

    @Test
    void assembleForPrefixesIncludesOnlyRequestedPrefixSkills() {
        String result = assembler.assembleForPrefixes(
                "Router base",
                Set.of("gitlab"));

        assertTrue(result.contains("Router base"));
        assertTrue(result.contains("## Workflow guidance for routing"));
        assertTrue(result.contains("### GitLab"));
        assertTrue(result.contains("project_id"));
        assertFalse(result.contains("### Jira"));
        assertFalse(result.contains("Connected MCP guidance"));
    }

    @Test
    void assembleForPrefixesIncludesCrossWorkflowWhenBothPrefixesProvided() {
        String result = assembler.assembleForPrefixes(
                "Router base",
                Set.of("jira", "red"));

        assertTrue(result.contains("### Cross-server workflows"));
        assertTrue(result.contains("#### Jira → RED audit log investigation"));
        assertTrue(result.contains("### Jira"));
        assertTrue(result.contains("### RED"));
    }

    @Test
    void hasGuidanceForPrefixesReturnsTrueForKnownPrefix() {
        assertTrue(assembler.hasGuidanceForPrefixes(Set.of("gitlab")));
        assertFalse(assembler.hasGuidanceForPrefixes(Set.of("unknown")));
        assertFalse(assembler.hasGuidanceForPrefixes(Set.of()));
    }

    private static McpTool redTool(String name) {
        return new McpTool(name, "desc", "conn-red", "{\"type\":\"object\"}");
    }

    private static McpTool gitlabTool(String name) {
        return new McpTool(name, "desc", "conn-gitlab", "{\"type\":\"object\"}");
    }

    private static McpTool jiraTool(String name) {
        return new McpTool(name, "desc", "conn-jira", "{\"type\":\"object\"}");
    }
}
