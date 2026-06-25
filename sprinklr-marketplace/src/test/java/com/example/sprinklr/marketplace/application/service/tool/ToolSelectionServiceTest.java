package com.example.sprinklr.marketplace.application.service.tool;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolSelectionServiceTest {

    private final ToolRouterPort router = mock(ToolRouterPort.class);
    private final McpProperties properties = new McpProperties();
    private final ToolSelectionService service = new ToolSelectionService(router, properties);

    private final McpTool getResources = tool("jira.getAccessibleResources");
    private final McpTool getMeta = tool("jira.getMeta");
    private final McpTool createIssue = tool("jira.createJiraIssue");
    private final McpTool searchIssues = tool("jira.searchIssues");
    private final List<McpTool> allTools = List.of(getResources, getMeta, createIssue, searchIssues);

    // createIssue -> getMeta -> getResources  (transitive chain)
    private final ToolDependencyGraph jiraGraph = new ToolDependencyGraph(
            "jira",
            Map.of(
                    "jira.createJiraIssue", List.of("jira.getMeta"),
                    "jira.getMeta", List.of("jira.getAccessibleResources")
            ),
            "fp",
            Instant.now(),
            DependencyGraphStatus.READY
    );

    @Test
    void expandsPrerequisitesBeforeTheSelectedTool() {
        when(router.selectToolNames(any(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of("jira.createJiraIssue"));

        ToolSelectionResult result = service.selectTools(
                "create a story in ITOPS", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(
                List.of("jira.getAccessibleResources", "jira.getMeta", "jira.createJiraIssue"),
                names(result));
        assertEquals(List.of("jira"), result.activeServerPrefixes());
    }

    @Test
    void fallsBackToAllToolsWhenRouterSelectsNothing() {
        when(router.selectToolNames(any(), anyList(), anyList(), anyInt())).thenReturn(List.of());

        ToolSelectionResult result = service.selectTools(
                "hello there", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(allTools.size(), result.scopedTools().size());
    }

    @Test
    void capsScopedToolsToConfiguredMax() {
        properties.getToolSelection().setMaxTools(2);
        when(router.selectToolNames(any(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of("jira.createJiraIssue"));

        ToolSelectionResult result = service.selectTools(
                "create", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        // Prerequisites are kept first when trimming to the cap.
        assertEquals(List.of("jira.getAccessibleResources", "jira.getMeta"), names(result));
    }

    @Test
    void continuationSkipsSatisfiedPrerequisitesAndInjectsContext() {
        when(router.selectToolNames(any(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of("jira.createJiraIssue"));
        PendingWorkflowState continuation = new PendingWorkflowState(
                "conv-1",
                "user-1",
                List.of("jira"),
                List.of("jira.getAccessibleResources", "jira.getMeta"),
                List.of("jira.getMeta -> {issueTypes:[Story]}"),
                Instant.now().plusSeconds(3600)
        );

        ToolSelectionResult result = service.selectTools(
                "summary: fix login", List.of(), allTools, List.of(jiraGraph), Optional.of(continuation));

        assertEquals(List.of("jira.createJiraIssue"), names(result));
        assertTrue(result.hasContinuationContext());
        assertTrue(result.continuationContext().contains("jira.getMeta"));
    }

    @Test
    void ignoresContinuationFromUnrelatedServer() {
        when(router.selectToolNames(any(), anyList(), anyList(), anyInt()))
                .thenReturn(List.of("jira.createJiraIssue"));
        PendingWorkflowState gitlabContinuation = new PendingWorkflowState(
                "conv-1", "user-1", List.of("gitlab"),
                List.of("gitlab.get_pipeline"), List.of("gitlab.get_pipeline -> ok"),
                Instant.now().plusSeconds(3600));

        ToolSelectionResult result = service.selectTools(
                "create", List.of(), allTools, List.of(jiraGraph), Optional.of(gitlabContinuation));

        assertFalse(result.hasContinuationContext());
        assertEquals(
                List.of("jira.getAccessibleResources", "jira.getMeta", "jira.createJiraIssue"),
                names(result));
    }

    private List<String> names(ToolSelectionResult result) {
        return result.scopedTools().stream().map(McpTool::name).toList();
    }

    private McpTool tool(String name) {
        return new McpTool(name, "description for " + name, "conn-jira", "{}");
    }
}
