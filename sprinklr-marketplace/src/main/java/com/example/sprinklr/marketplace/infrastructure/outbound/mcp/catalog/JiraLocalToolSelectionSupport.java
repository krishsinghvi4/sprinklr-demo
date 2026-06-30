package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic prerequisite expansion for locally extended Jira tools.
 */
@Component
public class JiraLocalToolSelectionSupport {

    private static final Map<String, List<String>> PREREQUISITES = Map.of(
            "jira.getJiraIssueChangelog", List.of("jira.getAccessibleAtlassianResources")
    );

    public boolean hasLocalPrerequisites(String fullyQualifiedToolName) {
        return PREREQUISITES.containsKey(fullyQualifiedToolName);
    }

    public List<String> prerequisitesFor(String fullyQualifiedToolName, Set<String> availableToolNames) {
        List<String> configured = PREREQUISITES.get(fullyQualifiedToolName);
        if (configured == null || configured.isEmpty()) {
            return List.of(fullyQualifiedToolName);
        }
        List<String> ordered = new java.util.ArrayList<>();
        for (String prerequisite : configured) {
            if (availableToolNames.contains(prerequisite)) {
                ordered.add(prerequisite);
            }
        }
        if (availableToolNames.contains(fullyQualifiedToolName)) {
            ordered.add(fullyQualifiedToolName);
        }
        return ordered.isEmpty() ? List.of(fullyQualifiedToolName) : ordered;
    }

    public static Map<String, List<String>> prerequisitesMap() {
        return new LinkedHashMap<>(PREREQUISITES);
    }
}
