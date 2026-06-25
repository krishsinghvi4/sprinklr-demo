package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * Outcome of the chat-time tool selection pass.
 * <p>
 * {@code scopedTools} is the small set (router picks + their expanded prerequisites, capped) that is
 * actually sent to the agent LLM with full schemas — instead of every connected tool. They are ordered
 * so prerequisites precede the tools that depend on them.
 * <p>
 * {@code continuationContext}, when non-blank, carries compact summaries of tool results from an earlier
 * turn of the same workflow so the agent can finish a multi-step action without re-fetching metadata.
 */
public record ToolSelectionResult(
        List<McpTool> scopedTools,
        List<String> activeServerPrefixes,
        String continuationContext
) {

    public ToolSelectionResult {
        scopedTools = scopedTools == null ? List.of() : List.copyOf(scopedTools);
        activeServerPrefixes = activeServerPrefixes == null ? List.of() : List.copyOf(activeServerPrefixes);
    }

    public boolean hasContinuationContext() {
        return continuationContext != null && !continuationContext.isBlank();
    }
}
