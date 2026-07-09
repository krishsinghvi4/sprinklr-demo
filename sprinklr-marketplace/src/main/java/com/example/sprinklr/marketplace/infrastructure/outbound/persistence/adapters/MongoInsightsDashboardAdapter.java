package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters;

import com.example.sprinklr.marketplace.domain.model.insights.DashboardConversation;
import com.example.sprinklr.marketplace.domain.model.insights.DashboardTurn;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.InsightsDashboardPort;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.DashboardConversationDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.DashboardTurnDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.DashboardConversationRepository;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.DashboardTurnRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class MongoInsightsDashboardAdapter implements InsightsDashboardPort {

    private final DashboardConversationRepository conversationRepository;
    private final DashboardTurnRepository turnRepository;

    public MongoInsightsDashboardAdapter(
            DashboardConversationRepository conversationRepository,
            DashboardTurnRepository turnRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.turnRepository = turnRepository;
    }

    @Override
    public List<DashboardConversation> findConversationsByUserId(String userId) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .map(this::toDomainConversation)
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }

    @Override
    public Optional<DashboardConversation> findConversationByIdAndUserId(String id, String userId) {
        return conversationRepository.findByIdAndUserId(id, userId)
                .map(this::toDomainConversation)
                .blockOptional();
    }

    @Override
    public Optional<DashboardConversation> findConversationBySourceConversationId(
            String userId,
            String sourceConversationId
    ) {
        return conversationRepository.findByUserIdAndSourceConversationId(userId, sourceConversationId)
                .map(this::toDomainConversation)
                .blockOptional();
    }

    @Override
    public DashboardConversation saveConversation(DashboardConversation conversation) {
        DashboardConversationDocument saved = conversationRepository
                .save(toDocument(conversation))
                .block();
        return toDomainConversation(saved);
    }

    @Override
    public void deleteConversation(String id, String userId) {
        turnRepository.deleteByDashboardConversationIdAndUserId(id, userId).block();
        conversationRepository.deleteByIdAndUserId(id, userId).block();
    }

    @Override
    public void deleteBySourceConversationId(String userId, String sourceConversationId) {
        findConversationBySourceConversationId(userId, sourceConversationId)
                .ifPresent(conversation -> deleteConversation(conversation.id(), userId));
    }

    @Override
    public List<DashboardTurn> findTurnsByDashboardConversationId(String dashboardConversationId, String userId) {
        return turnRepository.findByDashboardConversationIdAndUserIdOrderByCreatedAtAsc(
                        dashboardConversationId, userId)
                .map(this::toDomainTurn)
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }

    @Override
    public Optional<DashboardTurn> findTurnById(String turnId, String userId) {
        return turnRepository.findByIdAndUserId(turnId, userId)
                .map(this::toDomainTurn)
                .blockOptional();
    }

    @Override
    public Optional<DashboardTurn> findTurnBySourceChatMessageId(String userId, String sourceChatMessageId) {
        if (sourceChatMessageId == null || sourceChatMessageId.isBlank()) {
            return Optional.empty();
        }
        return turnRepository.findByUserIdAndSourceChatMessageId(userId, sourceChatMessageId)
                .map(this::toDomainTurn)
                .blockOptional();
    }

    @Override
    public List<String> findSavedSourceMessageIds(String userId, String sourceConversationId) {
        return conversationRepository.findByUserIdAndSourceConversationId(userId, sourceConversationId)
                .flatMapMany(conv -> turnRepository.findByUserIdAndDashboardConversationIdOrderByCreatedAtAsc(
                        userId, conv.id()))
                .mapNotNull(doc -> doc.sourceChatMessageId())
                .filter(id -> id != null && !id.isBlank())
                .collectList()
                .blockOptional()
                .orElse(List.of());
    }

    @Override
    public DashboardTurn saveTurn(DashboardTurn turn) {
        DashboardTurnDocument saved = turnRepository.save(toDocument(turn)).block();
        return toDomainTurn(saved);
    }

    @Override
    public void deleteTurn(String turnId, String userId) {
        turnRepository.deleteByIdAndUserId(turnId, userId).block();
    }

    @Override
    public void touchConversation(String dashboardConversationId, String preview) {
        conversationRepository.findById(dashboardConversationId)
                .flatMap(doc -> conversationRepository.save(new DashboardConversationDocument(
                        doc.id(),
                        doc.userId(),
                        doc.sourceConversationId(),
                        doc.title(),
                        preview != null ? preview : doc.preview(),
                        doc.turnCount(),
                        doc.createdAt(),
                        Instant.now()
                )))
                .block();
    }

    private DashboardConversation toDomainConversation(DashboardConversationDocument doc) {
        return new DashboardConversation(
                doc.id(),
                doc.userId(),
                doc.sourceConversationId(),
                doc.title(),
                doc.preview(),
                doc.turnCount(),
                doc.createdAt(),
                doc.updatedAt()
        );
    }

    private DashboardConversationDocument toDocument(DashboardConversation conversation) {
        return new DashboardConversationDocument(
                conversation.id(),
                conversation.userId(),
                conversation.sourceConversationId(),
                conversation.title(),
                conversation.preview(),
                conversation.turnCount(),
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }

    private DashboardTurn toDomainTurn(DashboardTurnDocument doc) {
        List<WidgetSpec> widgets = doc.widgets() == null
                ? List.of()
                : doc.widgets().stream()
                        .map(w -> new WidgetSpec(w.id(), w.type(), w.title(), w.description(), w.data()))
                        .toList();
        return new DashboardTurn(
                doc.id(),
                doc.dashboardConversationId(),
                doc.userId(),
                doc.sourceChatMessageId(),
                doc.prompt(),
                doc.narrative(),
                doc.assistantContent(),
                widgets,
                doc.toolResultSnapshot(),
                doc.extendedInsights(),
                doc.version(),
                doc.previousVersionId(),
                doc.createdAt()
        );
    }

    private DashboardTurnDocument toDocument(DashboardTurn turn) {
        List<DashboardTurnDocument.WidgetSpecDocument> widgets = turn.widgets().stream()
                .map(w -> new DashboardTurnDocument.WidgetSpecDocument(
                        w.id(), w.type(), w.title(), w.description(), w.data()))
                .toList();
        return new DashboardTurnDocument(
                turn.id(),
                turn.dashboardConversationId(),
                turn.userId(),
                turn.sourceChatMessageId(),
                turn.prompt(),
                turn.narrative(),
                turn.assistantContent(),
                widgets,
                turn.toolResultSnapshot(),
                turn.extendedInsights(),
                turn.version(),
                turn.previousVersionId(),
                turn.createdAt()
        );
    }
}
