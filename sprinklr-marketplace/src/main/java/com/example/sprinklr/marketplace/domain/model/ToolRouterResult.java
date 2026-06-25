package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * Result of the stage-1 tool router: selected tool names plus an explicit outcome so callers can
 * distinguish "no tools needed" from "routing failed".
 */
public record ToolRouterResult(
        List<String> toolNames,
        RouterOutcome outcome
) {

    public ToolRouterResult {
        toolNames = toolNames == null ? List.of() : List.copyOf(toolNames);
        if (outcome == null) {
            throw new IllegalArgumentException("ToolRouterResult outcome must not be null");
        }
    }

    public static ToolRouterResult noToolsNeeded() {
        return new ToolRouterResult(List.of(), RouterOutcome.NO_TOOLS_NEEDED);
    }

    public static ToolRouterResult failed() {
        return new ToolRouterResult(List.of(), RouterOutcome.FAILED);
    }

    public static ToolRouterResult selected(List<String> toolNames) {
        return new ToolRouterResult(toolNames, RouterOutcome.TOOLS_SELECTED);
    }
}
