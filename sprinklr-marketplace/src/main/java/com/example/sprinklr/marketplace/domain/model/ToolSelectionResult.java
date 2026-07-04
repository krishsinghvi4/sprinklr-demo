package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * Outcome of the chat-time tool selection pass.
 * <p>
 * {@code scopedTools} is the small set (router picks + their expanded prerequisites, capped) that is
 * actually sent to the agent LLM with full schemas — instead of every connected tool. They are ordered
 * so prerequisites precede the tools that depend on them.
 * <p>
 * {@code primaryToolNames} are the router's direct picks before dependency expansion — used to decide
 * whether a multi-step workflow is still awaiting its goal tool on the next turn.
 * <p>
 * {@code continuationContext}, when non-blank, carries compact summaries of tool results from an earlier
 * turn of the same workflow so the agent can finish a multi-step action without re-fetching metadata.
 */
public record ToolSelectionResult(
        List<McpTool> scopedTools,
        List<String> activeServerPrefixes,
        List<String> primaryToolNames,
        String continuationContext,
        boolean continuationDiscarded
) {

    public ToolSelectionResult {
        scopedTools = scopedTools == null ? List.of() : List.copyOf(scopedTools);
        activeServerPrefixes = activeServerPrefixes == null ? List.of() : List.copyOf(activeServerPrefixes);
        primaryToolNames = primaryToolNames == null ? List.of() : List.copyOf(primaryToolNames);
    }

    public ToolSelectionResult(
            List<McpTool> scopedTools,
            List<String> activeServerPrefixes,
            List<String> primaryToolNames,
            String continuationContext
    ) {
        this(scopedTools, activeServerPrefixes, primaryToolNames, continuationContext, false);
    }

    public boolean hasContinuationContext() {
        return continuationContext != null && !continuationContext.isBlank();
    }
}
