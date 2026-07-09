package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.application.service.tool.ToolSelectionService;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.MCP.McpInvocationResult;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.tool.ToolCall;
import com.example.sprinklr.marketplace.domain.model.tool.ToolSelectionResult;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.LLM.LlmPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpInvocationPreflightPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpServerPort;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedQueryPreferencesToolDescriptionAugmenter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardRegenerateServiceTest {

    @Mock
    private LlmPort llmPort;
    @Mock
    private McpServerPort mcpServerPort;
    @Mock
    private McpRegistryPort mcpRegistryPort;
    @Mock
    private McpInvocationPreflightPort mcpInvocationPreflightPort;
    @Mock
    private InsightsDashboardService insightsDashboardService;
    @Mock
    private ToolSelectionService toolSelectionService;

    private McpProperties mcpProperties;
    private DashboardRegenerateService service;

    private final McpTool gitlabTool = new McpTool(
            "gitlab.list_pipelines",
            "List pipelines",
            "conn-gitlab",
            "{}"
    );
    private final McpTool jiraTool = new McpTool(
            "jira.searchJiraIssuesUsingJql",
            "Search issues",
            "conn-jira",
            "{}"
    );

    @BeforeEach
    void setUp() {
        mcpProperties = new McpProperties();
        service = new DashboardRegenerateService(
                llmPort,
                mcpServerPort,
                mcpRegistryPort,
                mcpProperties,
                mcpInvocationPreflightPort,
                insightsDashboardService,
                toolSelectionService,
                new RedQueryPreferencesToolDescriptionAugmenter(mcpRegistryPort)
        );
    }

    @Test
    void regenerate_usesToolSelectionNotJiraOnlyFallback() throws Exception {
        DashboardTurn turn = sampleTurn();
        when(mcpRegistryPort.findActiveToolsForUser("user-1")).thenReturn(List.of(gitlabTool, jiraTool));
        when(mcpRegistryPort.findActiveDependencyGraphsForUser("user-1")).thenReturn(List.of());
        when(toolSelectionService.selectTools(any(), any(), any(), any(), any()))
                .thenReturn(new ToolSelectionResult(List.of(gitlabTool), List.of("gitlab"), List.of("gitlab.list_pipelines"), null));

        ArgumentCaptor<LlmRequest> requestCaptor = ArgumentCaptor.forClass(LlmRequest.class);
        when(llmPort.complete(requestCaptor.capture()))
                .thenReturn(new LlmResponse("No charts needed.", List.of()));

        CountDownLatch done = new CountDownLatch(1);
        service.streamRegenerate("user-1", turn, null, completionSubscriber(done));

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(requestCaptor.getValue().tools()).containsExactly(gitlabTool);
        verify(insightsDashboardService).updateTurnAfterRegenerate(
                eq("user-1"),
                eq(turn),
                eq("show pipeline breakdown"),
                eq("No charts needed."),
                eq(List.of()),
                eq(null)
        );
    }

    @Test
    void regenerate_capturesLastSuccessfulToolResult() throws Exception {
        DashboardTurn turn = sampleTurn();
        when(mcpRegistryPort.findActiveToolsForUser("user-1")).thenReturn(List.of(gitlabTool));
        when(mcpRegistryPort.findActiveDependencyGraphsForUser("user-1")).thenReturn(List.of());
        when(toolSelectionService.selectTools(any(), any(), any(), any(), any()))
                .thenReturn(new ToolSelectionResult(List.of(gitlabTool), List.of("gitlab"), List.of("gitlab.list_pipelines"), null));
        when(mcpRegistryPort.findByUserIdAndServerIdPrefix("user-1", "gitlab")).thenReturn(Optional.empty());
        when(mcpInvocationPreflightPort.validate(any(), any()))
                .thenReturn(McpInvocationPreflightPort.PreflightResult.allow());
        when(mcpServerPort.invoke(any())).thenReturn(
                new McpInvocationResult("call-1", true, "{\"pipelines\":[{\"status\":\"success\"}]}", null)
        );

        when(llmPort.complete(any()))
                .thenReturn(new LlmResponse("", List.of(new ToolCall("call-1", "gitlab.list_pipelines", "{}"))))
                .thenReturn(new LlmResponse("Done.", List.of()));

        CountDownLatch done = new CountDownLatch(1);
        service.streamRegenerate("user-1", turn, "edited prompt", completionSubscriber(done));

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        verify(insightsDashboardService).updateTurnAfterRegenerate(
                eq("user-1"),
                eq(turn),
                eq("edited prompt"),
                eq("Done."),
                eq(List.of()),
                eq("{\"pipelines\":[{\"status\":\"success\"}]}")
        );
    }

    private static DashboardTurn sampleTurn() {
        return new DashboardTurn(
                "dash-turn-1",
                "dash-conv-1",
                "user-1",
                "msg-1",
                "show pipeline breakdown",
                "narrative",
                "content",
                List.of(new WidgetSpec("w1", "bar", "Old", null, Map.of(
                        "labels", List.of("A"),
                        "values", List.of(1),
                        "xAxisLabel", "X",
                        "yAxisLabel", "Y"
                ))),
                null,
                Map.of(),
                1,
                null,
                Instant.parse("2026-01-15T10:00:00Z")
        );
    }

    private static Flow.Subscriber<String> completionSubscriber(CountDownLatch done) {
        return new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
            }

            @Override
            public void onError(Throwable throwable) {
                done.countDown();
            }

            @Override
            public void onComplete() {
                done.countDown();
            }
        };
    }
}
