package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.domain.port.outbound.InsightsDashboardPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightsDashboardServiceTest {

    @Mock
    private InsightsDashboardPort insightsDashboardPort;

    @Mock
    private ChatHistoryPort chatHistoryPort;

    private InsightsDashboardService service;

    private static final Instant FIXED_CREATED = Instant.parse("2026-01-15T10:00:00Z");

    @BeforeEach
    void setUp() {
        service = new InsightsDashboardService(insightsDashboardPort, chatHistoryPort);
    }

    @Test
    void updateTurnAfterRegenerate_preservesCreatedAtAndUpdatesPrompt() {
        DashboardTurn existing = sampleTurn("dash-turn-1", "old prompt", List.of(widget("w1")), FIXED_CREATED);
        when(insightsDashboardPort.saveTurn(any())).thenAnswer(invocation -> invocation.getArgument(0));

        String assistantContent = """
                Summary text.

                ```widget
                {"version":1,"widgets":[{"id":"w2","type":"bar","title":"Counts","data":{"labels":["A"],"values":[1],"xAxisLabel":"X","yAxisLabel":"Y"}}]}
                ```
                """;

        Optional<DashboardTurn> result = service.updateTurnAfterRegenerate(
                "user-1",
                existing,
                "new prompt",
                assistantContent,
                List.of(widget("w2")),
                "{\"data\":true}"
        );

        assertThat(result).isPresent();
        DashboardTurn updated = result.get();
        assertThat(updated.createdAt()).isEqualTo(FIXED_CREATED);
        assertThat(updated.prompt()).isEqualTo("new prompt");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.widgets()).hasSize(1);
        assertThat(updated.extendedInsights()).isEmpty();
        assertThat(updated.toolResultSnapshot()).isEqualTo("{\"data\":true}");
    }

    @Test
    void updateTurnAfterRegenerate_emptyWidgets_usesGracefulMessage() {
        DashboardTurn existing = sampleTurn("dash-turn-2", "show metrics", List.of(widget("w1")), FIXED_CREATED);
        when(insightsDashboardPort.saveTurn(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<DashboardTurn> result = service.updateTurnAfterRegenerate(
                "user-1",
                existing,
                "show metrics",
                "Here is a plain answer with no charts.",
                List.of(),
                null
        );

        assertThat(result).isPresent();
        DashboardTurn updated = result.get();
        assertThat(updated.widgets()).isEmpty();
        assertThat(updated.narrative()).contains("plain answer");
        assertThat(updated.createdAt()).isEqualTo(FIXED_CREATED);
    }

    @Test
    void updateTurnAfterRegenerate_emptyWidgetsAndNoProse_usesDefaultMessage() {
        DashboardTurn existing = sampleTurn("dash-turn-3", "pipeline stats", List.of(), FIXED_CREATED);
        when(insightsDashboardPort.saveTurn(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<DashboardTurn> result = service.updateTurnAfterRegenerate(
                "user-1",
                existing,
                "pipeline stats",
                "",
                List.of(),
                null
        );

        assertThat(result).isPresent();
        assertThat(result.get().narrative()).isEqualTo(InsightsDashboardService.NO_ANALYTICS_MESSAGE);
        assertThat(result.get().widgets()).isEmpty();
    }

    @Test
    void saveTurnFromChat_capturesToolSnapshotFromChatHistoryWhenMissing() {
        when(insightsDashboardPort.findTurnBySourceChatMessageId("user-1", "msg-asst")).thenReturn(Optional.empty());
        when(insightsDashboardPort.findConversationBySourceConversationId("user-1", "conv-1"))
                .thenReturn(Optional.empty());
        when(chatHistoryPort.collectToolResultSnapshotForAssistantMessage("conv-1", "msg-asst"))
                .thenReturn(Optional.of("{\"hits\":[]}"));
        when(insightsDashboardPort.saveConversation(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(insightsDashboardPort.saveTurn(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InsightsDashboardService.SaveTurnRequest request = new InsightsDashboardService.SaveTurnRequest(
                "conv-1",
                "msg-asst",
                "show audit counts",
                "summary\n\n```widget\n{\"version\":1,\"widgets\":[{\"id\":\"w1\",\"type\":\"bar\",\"title\":\"T\",\"data\":{\"labels\":[\"A\"],\"values\":[1],\"xAxisLabel\":\"X\",\"yAxisLabel\":\"Y\"}}]}\n```",
                List.of(widget("w1")),
                null
        );

        InsightsDashboardService.SaveTurnResult result = service.saveTurnFromChat("user-1", request);

        assertThat(result.alreadySaved()).isFalse();
        ArgumentCaptor<DashboardTurn> turnCaptor = ArgumentCaptor.forClass(DashboardTurn.class);
        verify(insightsDashboardPort).saveTurn(turnCaptor.capture());
        assertThat(turnCaptor.getValue().toolResultSnapshot()).isEqualTo("{\"hits\":[]}");
    }

    private static DashboardTurn sampleTurn(
            String id,
            String prompt,
            List<WidgetSpec> widgets,
            Instant createdAt
    ) {
        return new DashboardTurn(
                id,
                "dash-conv-1",
                "user-1",
                "msg-asst",
                prompt,
                "narrative",
                "content",
                widgets,
                null,
                Map.of(),
                1,
                null,
                createdAt
        );
    }

    private static WidgetSpec widget(String id) {
        return new WidgetSpec(id, "bar", "Title", null, Map.of(
                "labels", List.of("A"),
                "values", List.of(1),
                "xAxisLabel", "X",
                "yAxisLabel", "Y"
        ));
    }
}
