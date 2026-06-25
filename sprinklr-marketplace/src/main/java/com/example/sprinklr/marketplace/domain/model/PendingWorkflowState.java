package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Cross-turn continuation state for a multi-step tool workflow (e.g. Jira create: gather required
 * fields on turn 1, create on turn 2). Persisted per conversation so a follow-up turn can reuse the
 * tool results already gathered instead of re-running prerequisite/metadata tools.
 *
 * @param serverPrefixes       servers whose tools produced this state — continuation only applies when
 *                             the next turn selects tools from at least one of these prefixes
 * @param satisfiedToolNames   fully-qualified tools already executed this workflow; the expander skips
 *                             re-adding them as prerequisites
 * @param toolResultSummaries  compact, human-readable summaries of those tool results, injected into the
 *                             agent's context on the follow-up turn
 * @param expiresAt            TTL boundary; expired state is ignored and reaped
 */
public record PendingWorkflowState(
        String conversationId,
        String userId,
        List<String> serverPrefixes,
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
        satisfiedToolNames = satisfiedToolNames == null ? List.of() : List.copyOf(satisfiedToolNames);
        toolResultSummaries = toolResultSummaries == null ? List.of() : List.copyOf(toolResultSummaries);
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && now.isAfter(expiresAt);
    }
}
