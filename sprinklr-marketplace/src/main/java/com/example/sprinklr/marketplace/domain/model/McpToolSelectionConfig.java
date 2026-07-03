package com.example.sprinklr.marketplace.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Per-server tool-selection overrides from the catalog.
 */
public record McpToolSelectionConfig(
        List<String> continuationNeverSatisfyTools,
        Map<String, List<String>> staticDependencyGraph
) {

    public McpToolSelectionConfig(List<String> continuationNeverSatisfyTools) {
        this(continuationNeverSatisfyTools, Map.of());
    }

    public McpToolSelectionConfig {
        continuationNeverSatisfyTools = continuationNeverSatisfyTools == null
                ? List.of()
                : List.copyOf(continuationNeverSatisfyTools);
        staticDependencyGraph = staticDependencyGraph == null
                ? Map.of()
                : Map.copyOf(staticDependencyGraph);
    }
}
