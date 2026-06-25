package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;
import com.example.sprinklr.marketplace.domain.port.outbound.PendingWorkflowPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Mongo-backed continuation store. Persists one workflow state per conversation and enforces user
 * ownership and TTL expiry on read.
 */
@Component
public class MongoPendingWorkflowAdapter implements PendingWorkflowPort {

    private static final Logger log = LoggerFactory.getLogger(MongoPendingWorkflowAdapter.class);

    private final PendingWorkflowRepository repository;

    public MongoPendingWorkflowAdapter(PendingWorkflowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void save(PendingWorkflowState state) {
        repository.save(new PendingWorkflowDocument(
                state.conversationId(),
                state.userId(),
                state.serverPrefixes(),
                state.satisfiedToolNames(),
                state.toolResultSummaries(),
                state.expiresAt()
        ));
        log.info("[PendingWorkflow] Saved continuation conversation={} prefixes={} satisfiedTools={}",
                state.conversationId(), state.serverPrefixes(), state.satisfiedToolNames().size());
    }

    @Override
    public Optional<PendingWorkflowState> find(String conversationId, String userId) {
        return repository.findById(conversationId)
                .filter(document -> userId.equals(document.userId()))
                .filter(document -> document.expiresAt() == null || document.expiresAt().isAfter(Instant.now()))
                .map(this::toDomain);
    }

    @Override
    public void delete(String conversationId) {
        repository.deleteById(conversationId);
        log.debug("[PendingWorkflow] Deleted continuation conversation={}", conversationId);
    }

    private PendingWorkflowState toDomain(PendingWorkflowDocument document) {
        return new PendingWorkflowState(
                document.conversationId(),
                document.userId(),
                document.serverPrefixes(),
                document.satisfiedToolNames(),
                document.toolResultSummaries(),
                document.expiresAt()
        );
    }
}
