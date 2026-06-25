package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.PendingWorkflowState;

import java.util.Optional;

/**
 * Persistence for cross-turn workflow continuation state, keyed by conversation.
 * One in-progress workflow is tracked per conversation; saving overwrites the previous state.
 */
public interface PendingWorkflowPort {

    void save(PendingWorkflowState state);

    /** Returns the active (non-expired) state for the conversation, if any. */
    Optional<PendingWorkflowState> find(String conversationId, String userId);

    void delete(String conversationId);
}
