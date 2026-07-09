package com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers;

import com.example.sprinklr.marketplace.application.service.insights.DashboardRegenerateService;
import com.example.sprinklr.marketplace.application.service.insights.InsightsDashboardService;
import com.example.sprinklr.marketplace.application.service.insights.InsightsExpansionService;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardConversation;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.DashboardConversationDetailResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.DashboardConversationListResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.DashboardConversationSummaryDto;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.DashboardTurnDetailResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.DashboardTurnSummaryDto;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.ExpandDashboardWidgetResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.ExpandWidgetRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.ExpandWidgetResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.RegenerateDashboardTurnRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.SaveDashboardTurnRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.SaveDashboardTurnResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.SavedMessagesResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.UpdateDashboardTurnPromptRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Dashboard.WidgetSpecDto;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1/insights")
public class InsightsController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final InsightsDashboardService insightsDashboardService;
    private final DashboardRegenerateService dashboardRegenerateService;
    private final InsightsExpansionService insightsExpansionService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public InsightsController(
            InsightsDashboardService insightsDashboardService,
            DashboardRegenerateService dashboardRegenerateService,
            InsightsExpansionService insightsExpansionService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.insightsDashboardService = insightsDashboardService;
        this.dashboardRegenerateService = dashboardRegenerateService;
        this.insightsExpansionService = insightsExpansionService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping(value = "/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    public DashboardConversationListResponse listConversations() {
        String userId = authenticatedUserResolver.requireUserId();
        List<DashboardConversationSummaryDto> conversations = insightsDashboardService.listConversations(userId)
                .stream()
                .map(this::toSummaryDto)
                .toList();
        return new DashboardConversationListResponse(conversations);
    }

    @GetMapping(value = "/saved-messages", produces = MediaType.APPLICATION_JSON_VALUE)
    public SavedMessagesResponse getSavedMessages(@RequestParam String sourceConversationId) {
        String userId = authenticatedUserResolver.requireUserId();
        return insightsDashboardService.getSavedMessages(userId, sourceConversationId)
                .map(info -> new SavedMessagesResponse(info.dashboardConversationId(), info.savedMessageIds()))
                .orElse(new SavedMessagesResponse(null, List.of()));
    }

    @GetMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DashboardConversationDetailResponse getConversation(@PathVariable String id) {
        String userId = authenticatedUserResolver.requireUserId();
        DashboardConversation conversation = insightsDashboardService.getConversation(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard conversation not found"));
        List<DashboardTurnSummaryDto> turns = insightsDashboardService.listTurns(userId, id).stream()
                .map(this::toTurnSummaryDto)
                .toList();
        return new DashboardConversationDetailResponse(
                conversation.id(),
                conversation.sourceConversationId(),
                conversation.title(),
                conversation.preview(),
                conversation.turnCount(),
                conversation.createdAt(),
                conversation.updatedAt(),
                turns
        );
    }

    @GetMapping(value = "/conversations/{id}/turns/{turnId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DashboardTurnDetailResponse getTurn(@PathVariable String id, @PathVariable String turnId) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.getConversation(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard conversation not found"));
        DashboardTurn turn = insightsDashboardService.getTurn(userId, turnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard turn not found"));
        if (!turn.dashboardConversationId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard turn not found");
        }
        return toTurnDetailDto(turn);
    }

    @PostMapping(value = "/conversations/{id}/turns", produces = MediaType.APPLICATION_JSON_VALUE)
    public SaveDashboardTurnResponse saveTurn(
            @PathVariable String id,
            @RequestBody SaveDashboardTurnRequest request
    ) {
        String userId = authenticatedUserResolver.requireUserId();
        // id may be "new" — backend resolves from sourceConversationId
        InsightsDashboardService.SaveTurnResult result = insightsDashboardService.saveTurnFromChat(
                userId,
                new InsightsDashboardService.SaveTurnRequest(
                        request.sourceConversationId(),
                        request.sourceChatMessageId(),
                        request.prompt(),
                        request.assistantContent(),
                        toWidgetSpecs(request.widgets()),
                        request.toolResultSnapshot()
                )
        );
        return new SaveDashboardTurnResponse(
                result.conversation().id(),
                result.turn().id(),
                result.alreadySaved()
        );
    }

    @PostMapping(value = "/turns", produces = MediaType.APPLICATION_JSON_VALUE)
    public SaveDashboardTurnResponse saveTurnFromChat(@RequestBody SaveDashboardTurnRequest request) {
        String userId = authenticatedUserResolver.requireUserId();
        InsightsDashboardService.SaveTurnResult result = insightsDashboardService.saveTurnFromChat(
                userId,
                new InsightsDashboardService.SaveTurnRequest(
                        request.sourceConversationId(),
                        request.sourceChatMessageId(),
                        request.prompt(),
                        request.assistantContent(),
                        toWidgetSpecs(request.widgets()),
                        request.toolResultSnapshot()
                )
        );
        return new SaveDashboardTurnResponse(
                result.conversation().id(),
                result.turn().id(),
                result.alreadySaved()
        );
    }

    @PutMapping(value = "/conversations/{id}/turns/{turnId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public DashboardTurnDetailResponse updatePrompt(
            @PathVariable String id,
            @PathVariable String turnId,
            @RequestBody UpdateDashboardTurnPromptRequest request
    ) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.getConversation(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard conversation not found"));
        DashboardTurn turn = insightsDashboardService.updatePrompt(userId, turnId, request.prompt())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard turn not found"));
        return toTurnDetailDto(turn);
    }

    @PostMapping(value = "/conversations/{id}/turns/{turnId}/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerateTurn(
            @PathVariable String id,
            @PathVariable String turnId,
            @RequestBody(required = false) RegenerateDashboardTurnRequest request
    ) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.getConversation(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard conversation not found"));
        DashboardTurn turn = insightsDashboardService.getTurn(userId, turnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard turn not found"));

        String promptOverride = request != null ? request.prompt() : null;

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                try {
                    emitter.send(SseEmitter.event().data(item));
                } catch (IOException exception) {
                    emitter.completeWithError(exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                try {
                    emitter.send(SseEmitter.event().data(LlmErrorFormatter.toUserMessage(throwable)));
                    emitter.complete();
                } catch (IOException exception) {
                    emitter.completeWithError(exception);
                }
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }
        };

        dashboardRegenerateService.streamRegenerate(userId, turn, promptOverride, subscriber);
        return emitter;
    }

    @DeleteMapping(value = "/conversations/{id}/turns/{turnId}")
    public void deleteTurn(@PathVariable String id, @PathVariable String turnId) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.deleteTurn(userId, id, turnId);
    }

    @DeleteMapping(value = "/conversations/{id}")
    public void deleteConversation(@PathVariable String id) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.deleteConversation(userId, id);
    }

    @PostMapping(value = "/conversations/{id}/turns/{turnId}/widgets/{widgetId}/expand", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExpandDashboardWidgetResponse expandDashboardWidget(
            @PathVariable String id,
            @PathVariable String turnId,
            @PathVariable String widgetId,
            @RequestBody(required = false) ExpandWidgetRequest request
    ) {
        String userId = authenticatedUserResolver.requireUserId();
        insightsDashboardService.getConversation(userId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard conversation not found"));
        DashboardTurn turn = insightsDashboardService.getTurn(userId, turnId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dashboard turn not found"));

        WidgetSpec widget = turn.widgets().stream()
                .filter(w -> w.id().equals(widgetId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Widget not found"));

        String focus = request != null ? request.userFocus() : null;
        String snapshot = request != null && request.toolResultSnapshot() != null
                ? request.toolResultSnapshot()
                : turn.toolResultSnapshot();
        String markdown = insightsExpansionService.expandWidget(userId, widget, snapshot, focus);
        insightsDashboardService.storeExtendedInsight(userId, turnId, widgetId, markdown);
        return new ExpandDashboardWidgetResponse(markdown, turnId, widgetId);
    }

    @PostMapping(value = "/expand", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExpandWidgetResponse expandInChat(@RequestBody ExpandWidgetRequest request) {
        String userId = authenticatedUserResolver.requireUserId();
        if (request.widget() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Widget is required");
        }
        WidgetSpec widget = toWidgetSpec(request.widget());
        String markdown = insightsExpansionService.expandWidget(
                userId,
                widget,
                request.toolResultSnapshot(),
                request.userFocus()
        );
        return new ExpandWidgetResponse(markdown);
    }

    private DashboardConversationSummaryDto toSummaryDto(DashboardConversation conversation) {
        return new DashboardConversationSummaryDto(
                conversation.id(),
                conversation.sourceConversationId(),
                conversation.title(),
                conversation.preview(),
                conversation.turnCount(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }

    private DashboardTurnSummaryDto toTurnSummaryDto(DashboardTurn turn) {
        return new DashboardTurnSummaryDto(
                turn.id(),
                turn.prompt(),
                turn.version(),
                turn.createdAt()
        );
    }

    private DashboardTurnDetailResponse toTurnDetailDto(DashboardTurn turn) {
        return new DashboardTurnDetailResponse(
                turn.id(),
                turn.dashboardConversationId(),
                turn.sourceChatMessageId(),
                turn.prompt(),
                turn.narrative(),
                turn.assistantContent(),
                turn.widgets().stream().map(this::toWidgetDto).toList(),
                turn.toolResultSnapshot(),
                turn.extendedInsights(),
                turn.version(),
                turn.previousVersionId(),
                turn.createdAt()
        );
    }

    private WidgetSpecDto toWidgetDto(WidgetSpec widget) {
        return new WidgetSpecDto(
                widget.id(),
                widget.type(),
                widget.title(),
                widget.description(),
                widget.data()
        );
    }

    private WidgetSpec toWidgetSpec(WidgetSpecDto dto) {
        return new WidgetSpec(dto.id(), dto.type(), dto.title(), dto.description(), dto.data());
    }

    private List<WidgetSpec> toWidgetSpecs(List<WidgetSpecDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(this::toWidgetSpec).toList();
    }
}
