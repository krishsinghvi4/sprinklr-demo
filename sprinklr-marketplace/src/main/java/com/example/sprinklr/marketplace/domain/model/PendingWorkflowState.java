package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Cross-turn continuation state for a multi-step tool workflow (e.g. Jira create: gather required
 * fields on turn 1, create on turn 2). Persisted per conversation so a follow-up turn can reuse workflow
 * intent and non-rerunnable context instead of re-asking the user.
 *
 * @param serverPrefixes       servers whose tools produced this state
 * @param awaitingGoalTools    router primary goal tools not yet executed when this state was saved
 * @param satisfiedToolNames   fully-qualified tools already executed this workflow; the expander skips
 *                             re-adding them as prerequisites (except tools on the never-satisfy denylist)
 * @param toolResultSummaries  compact, human-readable summaries injected into the agent's context
 * @param expiresAt            TTL boundary; expired state is ignored and reaped
 */
public record PendingWorkflowState(
        String conversationId,
        String userId,
        List<String> serverPrefixes,
        List<String> awaitingGoalTools,
        List<String> satisfiedToolNames,
        List<String> toolResultSummaries,
        Instant expiresAt
) {

    public PendingWorkflowState {
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("PendingWorkflowState conversationId must not be blank");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("PendingWorkflowState userId must not be blank");
        }
        serverPrefixes = serverPrefixes == null ? List.of() : List.copyOf(serverPrefixes);
        awaitingGoalTools = awaitingGoalTools == null ? List.of() : List.copyOf(awaitingGoalTools);
        satisfiedToolNames = satisfiedToolNames == null ? List.of() : List.copyOf(satisfiedToolNames);
        toolResultSummaries = toolResultSummaries == null ? List.of() : List.copyOf(toolResultSummaries);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
