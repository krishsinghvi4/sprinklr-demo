package com.example.sprinklr.marketplace.application.service.tool;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.model.RouterOutcome;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.model.ToolRouterResult;
import com.example.sprinklr.marketplace.domain.model.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.port.outbound.ToolRouterPort;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogToolSelectionSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolSelectionServiceTest {

    private final ToolRouterPort router = mock(ToolRouterPort.class);
    private final McpProperties properties = new McpProperties();
    private final McpCatalogToolSelectionSupport catalogToolSelectionSupport =
            mock(McpCatalogToolSelectionSupport.class);
    private final ToolSelectionService service =
            new ToolSelectionService(router, properties, catalogToolSelectionSupport);

    private final McpTool getResources = tool("jira.getAccessibleAtlassianResources");
    private final McpTool getMeta = tool("jira.getJiraProjectIssueTypesMetadata");
    private final McpTool createIssue = tool("jira.createJiraIssue");
    private final McpTool searchIssues = tool("jira.searchJiraIssuesUsingJql");
    private final List<McpTool> allTools = List.of(getResources, getMeta, createIssue, searchIssues);

    @BeforeEach
    void stubCatalogNeverSatisfyTools() {
        when(catalogToolSelectionSupport.continuationNeverSatisfyToolsForToolNames(anyIterable()))
                .thenReturn(Set.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraProjectIssueTypesMetadata",
                        "jira.getJiraIssueTypeMetaWithFields"
                ));
    }

    private final ToolDependencyGraph jiraGraph = new ToolDependencyGraph(
            "jira",
            Map.of(
                    "jira.createJiraIssue", List.of("jira.getJiraProjectIssueTypesMetadata"),
                    "jira.getJiraProjectIssueTypesMetadata", List.of("jira.getAccessibleAtlassianResources"),
                    "jira.searchJiraIssuesUsingJql", List.of("jira.getAccessibleAtlassianResources")
            ),
            "fp",
            Instant.now(),
            DependencyGraphStatus.READY
    );

    @Test
    void expandsPrerequisitesBeforeTheSelectedTool() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.selected(List.of("jira.createJiraIssue")));

        ToolSelectionResult result = service.selectTools(
                "create a story in ITOPS", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(
                List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraProjectIssueTypesMetadata",
                        "jira.createJiraIssue"),
                names(result));
        assertEquals(List.of("jira"), result.activeServerPrefixes());
    }

    @Test
    void sendsZeroToolsForConversationalPromptWhenRouterFindsNothing() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.noToolsNeeded());

        ToolSelectionResult result = service.selectTools(
                "hello there", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(0, result.scopedTools().size());
        assertFalse(result.hasContinuationContext());
    }

    @Test
    void fallsBackToAllToolsWhenRouterFails() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.failed());

        ToolSelectionResult result = service.selectTools(
                "hello there", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(allTools.size(), result.scopedTools().size());
    }

    @Test
    void capsScopedToolsToConfiguredMax() {
        properties.getToolSelection().setMaxTools(2);
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.selected(List.of("jira.createJiraIssue")));

        ToolSelectionResult result = service.selectTools(
                "create", List.of(), allTools, List.of(jiraGraph), Optional.empty());

        assertEquals(
                List.of("jira.getAccessibleAtlassianResources", "jira.getJiraProjectIssueTypesMetadata"),
                names(result));
    }

    @Test
    void continuationSkipsNonRerunnablePrerequisitesAndInjectsContext() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.selected(List.of("jira.createJiraIssue")));
        PendingWorkflowState continuation = new PendingWorkflowState(
                "conv-1",
                "user-1",
                List.of("jira"),
                List.of("jira.createJiraIssue"),
                List.of("jira.getJiraProjectIssueTypesMetadata"),
                List.of("jira.getJiraProjectIssueTypesMetadata -> {issueTypes:[Story]}"),
                Instant.now().plusSeconds(3600)
        );

        ToolSelectionResult result = service.selectTools(
                "summary: fix login", List.of(), allTools, List.of(jiraGraph), Optional.of(continuation));

        assertEquals(
                List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraProjectIssueTypesMetadata",
                        "jira.createJiraIssue"),
                names(result));
        assertTrue(result.hasContinuationContext());
    }

    @Test
    void ignoresContinuationWhenAwaitingGoalsDoNotMatchCurrentPrimary() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.selected(List.of("jira.searchJiraIssuesUsingJql")));
        PendingWorkflowState createContinuation = new PendingWorkflowState(
                "conv-1",
                "user-1",
                List.of("jira"),
                List.of("jira.createJiraIssue"),
                List.of("jira.getAccessibleAtlassianResources"),
                List.of("jira.getAccessibleAtlassianResources -> ok"),
                Instant.now().plusSeconds(3600));

        ToolSelectionResult result = service.selectTools(
                "fetch pratham kundan tickets", List.of(), allTools, List.of(jiraGraph),
                Optional.of(createContinuation));

        assertFalse(result.hasContinuationContext());
        assertEquals(
                List.of("jira.getAccessibleAtlassianResources", "jira.searchJiraIssuesUsingJql"),
                names(result));
    }

    @Test
    void ignoresContinuationFromUnrelatedServer() {
        when(router.selectTools(any(), anyList(), anyList(), anyInt()))
                .thenReturn(ToolRouterResult.selected(List.of("jira.createJiraIssue")));
        PendingWorkflowState gitlabContinuation = new PendingWorkflowState(
                "conv-1", "user-1", List.of("gitlab"),
                List.of("gitlab.create_merge_request"),
                List.of("gitlab.get_pipeline"), List.of("gitlab.get_pipeline -> ok"),
                Instant.now().plusSeconds(3600));

        ToolSelectionResult result = service.selectTools(
                "create", List.of(), allTools, List.of(jiraGraph), Optional.of(gitlabContinuation));

        assertFalse(result.hasContinuationContext());
        assertEquals(
                List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraProjectIssueTypesMetadata",
                        "jira.createJiraIssue"),
                names(result));
    }

    private List<String> names(ToolSelectionResult result) {
        return result.scopedTools().stream().map(McpTool::name).toList();
    }

    private McpTool tool(String name) {
        return new McpTool(name, "description for " + name, "conn-jira", "{}");
    }
}
