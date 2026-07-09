package com.example.sprinklr.marketplace.domain.model.LLM;

/**
 * Outcome of the lightweight tool router LLM call.
 */
public enum RouterOutcome {
    /** Router succeeded and determined no MCP tools are needed for this turn. */
    NO_TOOLS_NEEDED,
    /** Router succeeded and selected one or more primary tools. */
    TOOLS_SELECTED,
    /** Router failed (LLM error, unparseable response) — caller should fall back to all tools. */
    FAILED
}
