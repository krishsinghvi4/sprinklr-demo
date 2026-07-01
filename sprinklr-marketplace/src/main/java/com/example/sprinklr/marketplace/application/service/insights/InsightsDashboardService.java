package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.domain.model.insights.DashboardConversation;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.InsightsDashboardPort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class InsightsDashboardService {

    private static final int PREVIEW_MAX = 80;

    private final InsightsDashboardPort insightsDashboardPort;

    public InsightsDashboardService(InsightsDashboardPort insightsDashboardPort) {
        this.insightsDashboardPort = insightsDashboardPort;
    }

    public List<DashboardConversation> listConversations(String userId) {
        return insightsDashboardPort.findConversationsByUserId(userId);
    }

    public Optional<DashboardConversation> getConversation(String userId, String dashboardConversationId) {
        return insightsDashboardPort.findConversationByIdAndUserId(dashboardConversationId, userId);
    }

    public List<DashboardTurn> listTurns(String userId, String dashboardConversationId) {
        if (insightsDashboardPort.findConversationByIdAndUserId(dashboardConversationId, userId).isEmpty()) {
            return List.of();
        }
        return insightsDashboardPort.findTurnsByDashboardConversationId(dashboardConversationId, userId);
    }

    public Optional<DashboardTurn> getTurn(String userId, String turnId) {
        return insightsDashboardPort.findTurnById(turnId, userId);
    }

    public SaveTurnResult saveTurnFromChat(String userId, SaveTurnRequest request) {
        Instant now = Instant.now();
        DashboardConversation conversation = insightsDashboardPort
                .findConversationBySourceConversationId(userId, request.sourceConversationId())
                .orElseGet(() -> createConversation(userId, request, now));

        String turnId = newId("dash-turn");
        String narrative = WidgetBlockParser.extractNarrative(request.assistantContent());
        DashboardTurn turn = new DashboardTurn(
                turnId,
                conversation.id(),
                userId,
                request.sourceChatMessageId(),
                request.prompt(),
                narrative,
                request.assistantContent(),
                request.widgets(),
                request.toolResultSnapshot(),
                java.util.Map.of(),
                1,
                null,
                now
        );
        insightsDashboardPort.saveTurn(turn);

        int newCount = conversation.turnCount() + 1;
        String preview = truncatePreview(request.prompt());
        DashboardConversation updated = new DashboardConversation(
                conversation.id(),
                conversation.userId(),
                conversation.sourceConversationId(),
                conversation.title(),
                preview,
                newCount,
                conversation.createdAt(),
                now
        );
        insightsDashboardPort.saveConversation(updated);

        return new SaveTurnResult(updated, turn);
    }

    public Optional<DashboardTurn> updatePrompt(String userId, String turnId, String prompt) {
        return insightsDashboardPort.findTurnById(turnId, userId)
                .map(existing -> {
                    DashboardTurn updated = new DashboardTurn(
                            existing.id(),
                            existing.dashboardConversationId(),
                            existing.userId(),
                            existing.sourceChatMessageId(),
                            prompt,
                            existing.narrative(),
                            existing.assistantContent(),
                            existing.widgets(),
                            existing.toolResultSnapshot(),
                            existing.extendedInsights(),
                            existing.version(),
                            existing.previousVersionId(),
                            existing.createdAt()
                    );
                    return insightsDashboardPort.saveTurn(updated);
                });
    }

    public Optional<DashboardTurn> updateTurnAfterRegenerate(
            String userId,
            DashboardTurn existing,
            String assistantContent,
            List<WidgetSpec> widgets,
            String toolResultSnapshot
    ) {
        String narrative = WidgetBlockParser.extractNarrative(assistantContent);
        DashboardTurn updated = new DashboardTurn(
                existing.id(),
                existing.dashboardConversationId(),
                existing.userId(),
                existing.sourceChatMessageId(),
                existing.prompt(),
                narrative,
                assistantContent,
                widgets,
                toolResultSnapshot != null ? toolResultSnapshot : existing.toolResultSnapshot(),
                existing.extendedInsights(),
                existing.version() + 1,
                existing.id(),
                Instant.now()
        );
        insightsDashboardPort.saveTurn(updated);
        insightsDashboardPort.touchConversation(existing.dashboardConversationId(), truncatePreview(existing.prompt()));
        return Optional.of(updated);
    }

    public void deleteTurn(String userId, String dashboardConversationId, String turnId) {
        if (insightsDashboardPort.findConversationByIdAndUserId(dashboardConversationId, userId).isEmpty()) {
            return;
        }
        insightsDashboardPort.deleteTurn(turnId, userId);
        decrementTurnCount(userId, dashboardConversationId);
    }

    public void deleteConversation(String userId, String dashboardConversationId) {
        insightsDashboardPort.deleteConversation(dashboardConversationId, userId);
    }

    public Optional<DashboardTurn> storeExtendedInsight(
            String userId,
            String turnId,
            String widgetId,
            String markdown
    ) {
        return insightsDashboardPort.findTurnById(turnId, userId)
                .map(existing -> {
                    java.util.Map<String, String> insights = new java.util.HashMap<>(existing.extendedInsights());
                    insights.put(widgetId, markdown);
                    DashboardTurn updated = new DashboardTurn(
                            existing.id(),
                            existing.dashboardConversationId(),
                            existing.userId(),
                            existing.sourceChatMessageId(),
                            existing.prompt(),
                            existing.narrative(),
                            existing.assistantContent(),
                            existing.widgets(),
                            existing.toolResultSnapshot(),
                            insights,
                            existing.version(),
                            existing.previousVersionId(),
                            existing.createdAt()
                    );
                    return insightsDashboardPort.saveTurn(updated);
                });
    }

    private void decrementTurnCount(String userId, String dashboardConversationId) {
        insightsDashboardPort.findConversationByIdAndUserId(dashboardConversationId, userId)
                .ifPresent(conv -> {
                    int count = Math.max(0, conv.turnCount() - 1);
                    insightsDashboardPort.saveConversation(new DashboardConversation(
                            conv.id(),
                            conv.userId(),
                            conv.sourceConversationId(),
                            conv.title(),
                            conv.preview(),
                            count,
                            conv.createdAt(),
                            Instant.now()
                    ));
                });
    }

    private DashboardConversation createConversation(String userId, SaveTurnRequest request, Instant now) {
        String title = truncatePreview(request.prompt());
        DashboardConversation conversation = new DashboardConversation(
                newId("dash-conv"),
                userId,
                request.sourceConversationId(),
                title,
                title,
                0,
                now,
                now
        );
        return insightsDashboardPort.saveConversation(conversation);
    }

    private static String truncatePreview(String text) {
        if (text == null) {
            return "Insights";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= PREVIEW_MAX) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX - 3) + "...";
    }

    private static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public record SaveTurnRequest(
            String sourceConversationId,
            String sourceChatMessageId,
            String prompt,
            String assistantContent,
            List<WidgetSpec> widgets,
            String toolResultSnapshot
    ) {}

    public record SaveTurnResult(DashboardConversation conversation, DashboardTurn turn) {}
}
