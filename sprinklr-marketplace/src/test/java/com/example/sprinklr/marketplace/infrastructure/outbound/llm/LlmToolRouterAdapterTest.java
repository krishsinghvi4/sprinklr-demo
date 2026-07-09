package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.LLM.RouterOutcome;
import com.example.sprinklr.marketplace.domain.model.tool.ToolRouterResult;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmProperties;
import com.example.sprinklr.marketplace.infrastructure.config.LLM.LlmSystemPromptLoader;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.JiraRedAuditToolSelectionSupport;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmToolRouterAdapterTest {

    @Mock
    private LlmService llmService;

    private LlmToolRouterAdapter adapter;

    private final List<McpTool> gitlabTools = List.of(
            gitlabTool("gitlab.list_merge_requests"),
            gitlabTool("gitlab.whoami"),
            gitlabTool("gitlab.get_merge_request")
    );

    private final List<McpTool> jiraRedTools = List.of(
            jiraTool("jira.getJiraIssue"),
            redTool("red.red_execute_audit_log_elasticsearch_query")
    );

    @BeforeEach
    void setUp() throws Exception {
        LlmProperties llmProperties = new LlmProperties();
        llmProperties.setStubEnabled(true);
        llmProperties.setSystemPromptPath("classpath:llm/system-prompt.txt");
        LlmSystemPromptLoader promptLoader = new LlmSystemPromptLoader(llmProperties, new DefaultResourceLoader());

        McpProperties mcpProperties = new McpProperties();
        mcpProperties.setCatalogPath("classpath:mcp/mcp-catalog.json");
        McpCatalogLoader catalogLoader = new McpCatalogLoader(mcpProperties, new DefaultResourceLoader());
        McpSkillPromptLoader skillPromptLoader = new McpSkillPromptLoader(catalogLoader, new DefaultResourceLoader());
        CrossWorkflowSkillLoader crossWorkflowSkillLoader =
                new CrossWorkflowSkillLoader(catalogLoader, new DefaultResourceLoader());
        McpSkillPromptAssembler skillPromptAssembler = new McpSkillPromptAssembler(
                promptLoader, skillPromptLoader, crossWorkflowSkillLoader);

        adapter = new LlmToolRouterAdapter(
                llmService,
                promptLoader,
                skillPromptAssembler,
                new JiraRedAuditToolSelectionSupport()
        );
    }

    @Test
    void pass2RunsWithGitLabSkillsWhenPass1SelectsGitLabTool() {
        when(llmService.complete(any()))
                .thenReturn(jsonResult("{\"selectedTools\": [\"gitlab.list_merge_requests\"]}"))
                .thenReturn(jsonResult(
                        "{\"selectedTools\": [\"gitlab.list_merge_requests\", \"gitlab.whoami\", \"gitlab.get_merge_request\"]}"));

        ToolRouterResult result = adapter.selectTools(
                "What is the status of my MR?",
                List.of(),
                gitlabTools,
                8
        );

        ArgumentCaptor<LlmCompletionCommand> captor = ArgumentCaptor.forClass(LlmCompletionCommand.class);
        verify(llmService, times(2)).complete(captor.capture());

        String pass1Prompt = captor.getAllValues().get(0).systemPromptOverride();
        String pass2Prompt = captor.getAllValues().get(1).systemPromptOverride();
        assertFalse(pass1Prompt.contains("Workflow guidance for routing"));
        assertTrue(pass2Prompt.contains("Workflow guidance for routing"));
        assertTrue(pass2Prompt.contains("project_id"));

        assertEquals(RouterOutcome.TOOLS_SELECTED, result.outcome());
        assertEquals(
                List.of("gitlab.list_merge_requests", "gitlab.whoami", "gitlab.get_merge_request"),
                result.toolNames()
        );
    }

    @Test
    void skipsPass2WhenPass1FindsNoToolsNeeded() {
        when(llmService.complete(any()))
                .thenReturn(jsonResult("{\"selectedTools\": []}"));

        ToolRouterResult result = adapter.selectTools("hello", List.of(), gitlabTools, 8);

        verify(llmService, times(1)).complete(any());
        assertEquals(RouterOutcome.NO_TOOLS_NEEDED, result.outcome());
    }

    @Test
    void auditIntentBootstrapsJiraAndRedSkillsOnPass2() {
        when(llmService.complete(any()))
                .thenReturn(jsonResult("{\"selectedTools\": [\"jira.getJiraIssue\"]}"))
                .thenReturn(jsonResult("{\"selectedTools\": [\"jira.getJiraIssue\", "
                        + "\"red.red_execute_audit_log_elasticsearch_query\"]}"));

        ToolRouterResult result = adapter.selectTools(
                "Investigate audit logs for ITOPS-123",
                List.of(),
                jiraRedTools,
                8
        );

        ArgumentCaptor<LlmCompletionCommand> captor = ArgumentCaptor.forClass(LlmCompletionCommand.class);
        verify(llmService, times(2)).complete(captor.capture());

        String pass2Prompt = captor.getAllValues().get(1).systemPromptOverride();
        assertTrue(pass2Prompt.contains("Jira → RED audit log investigation"));
        assertTrue(pass2Prompt.contains("### Jira"));
        assertTrue(pass2Prompt.contains("### RED"));

        assertEquals(
                List.of("jira.getJiraIssue", "red.red_execute_audit_log_elasticsearch_query"),
                result.toolNames()
        );
    }

    @Test
    void pass2FailureReturnsPass1Selection() {
        when(llmService.complete(any()))
                .thenReturn(jsonResult("{\"selectedTools\": [\"gitlab.list_merge_requests\"]}"))
                .thenReturn(jsonResult("not json"));

        ToolRouterResult result = adapter.selectTools(
                "Show my merge requests",
                List.of(),
                gitlabTools,
                8
        );

        verify(llmService, times(2)).complete(any());
        assertEquals(RouterOutcome.TOOLS_SELECTED, result.outcome());
        assertEquals(List.of("gitlab.list_merge_requests"), result.toolNames());
    }

    private static LlmCompletionResult jsonResult(String json) {
        return new LlmCompletionResult(json, List.of());
    }

    private static McpTool gitlabTool(String name) {
        return new McpTool(name, "desc", "conn-gitlab", "{\"type\":\"object\"}");
    }

    private static McpTool jiraTool(String name) {
        return new McpTool(name, "desc", "conn-jira", "{\"type\":\"object\"}");
    }

    private static McpTool redTool(String name) {
        return new McpTool(name, "desc", "conn-red", "{\"type\":\"object\"}");
    }
}
