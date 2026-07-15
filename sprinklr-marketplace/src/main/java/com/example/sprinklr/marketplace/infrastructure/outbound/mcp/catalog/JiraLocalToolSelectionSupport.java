package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic prerequisite expansion for locally extended Jira tools.
 */
@Component
public class JiraLocalToolSelectionSupport {

    private static final String GET_RESOURCES = "jira.getAccessibleAtlassianResources";
    private static final String LOOKUP_ACCOUNT = "jira.lookupJiraAccountId";

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"
    );

    private static final Map<String, List<String>> PREREQUISITES = Map.of(
            "jira.getJiraIssueChangelog", List.of(GET_RESOURCES)
    );

    private static final Set<String> SEARCH_TOOLS = Set.of(
            "jira.search",
            "jira.searchJiraIssuesUsingJql"
    );

    public boolean hasLocalPrerequisites(String fullyQualifiedToolName, String userPrompt) {
        if (isAssigneeEmailSearch(fullyQualifiedToolName, userPrompt)) {
            return true;
        }
        return PREREQUISITES.containsKey(fullyQualifiedToolName);
    }

    /** @deprecated prefer {@link #hasLocalPrerequisites(String, String)} */
    public boolean hasLocalPrerequisites(String fullyQualifiedToolName) {
        return hasLocalPrerequisites(fullyQualifiedToolName, null);
    }

    public List<String> prerequisitesFor(
            String fullyQualifiedToolName,
            Set<String> availableToolNames,
            String userPrompt
    ) {
        if (isAssigneeEmailSearch(fullyQualifiedToolName, userPrompt)) {
            return orderedAvailable(
                    List.of(GET_RESOURCES, LOOKUP_ACCOUNT, fullyQualifiedToolName),
                    availableToolNames,
                    fullyQualifiedToolName
            );
        }
        List<String> configured = PREREQUISITES.get(fullyQualifiedToolName);
        if (configured == null || configured.isEmpty()) {
            return List.of(fullyQualifiedToolName);
        }
        List<String> chain = new ArrayList<>(configured);
        chain.add(fullyQualifiedToolName);
        return orderedAvailable(chain, availableToolNames, fullyQualifiedToolName);
    }

    public List<String> prerequisitesFor(String fullyQualifiedToolName, Set<String> availableToolNames) {
        return prerequisitesFor(fullyQualifiedToolName, availableToolNames, null);
    }

    public static Map<String, List<String>> prerequisitesMap() {
        return new LinkedHashMap<>(PREREQUISITES);
    }

    private boolean isAssigneeEmailSearch(String fullyQualifiedToolName, String userPrompt) {
        return SEARCH_TOOLS.contains(fullyQualifiedToolName) && containsEmail(userPrompt);
    }

    private static boolean containsEmail(String userPrompt) {
        return userPrompt != null && !userPrompt.isBlank() && EMAIL.matcher(userPrompt).find();
    }

    private static List<String> orderedAvailable(
            List<String> preferredOrder,
            Set<String> availableToolNames,
            String fallback
    ) {
        List<String> ordered = new ArrayList<>();
        for (String tool : preferredOrder) {
            if (availableToolNames.contains(tool) && !ordered.contains(tool)) {
                ordered.add(tool);
            }
        }
        return ordered.isEmpty() ? List.of(fallback) : ordered;
    }
}
