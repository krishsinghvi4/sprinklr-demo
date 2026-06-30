package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * Per-server tool-selection overrides from the catalog.
 */
public record McpToolSelectionConfig(
        List<String> continuationNeverSatisfyTools,
        boolean skipDependencyGraph
) {

    public McpToolSelectionConfig(List<String> continuationNeverSatisfyTools) {
        this(continuationNeverSatisfyTools, false);
    }

    public McpToolSelectionConfig {
        continuationNeverSatisfyTools = continuationNeverSatisfyTools == null
                ? List.of()
                : List.copyOf(continuationNeverSatisfyTools);
    }
}
