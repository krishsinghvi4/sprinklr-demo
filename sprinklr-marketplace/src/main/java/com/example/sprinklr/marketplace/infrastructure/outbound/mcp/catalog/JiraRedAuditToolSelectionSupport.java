package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Expands cross-server tool selection for Jira ticket → RED audit log investigation workflows.
 */
@Component
public class JiraRedAuditToolSelectionSupport {

    private static final Pattern JIRA_KEY_PATTERN = Pattern.compile("\\b[A-Z][A-Z0-9]+-\\d+\\b");
    private static final Pattern AUDIT_INVESTIGATION_PATTERN = Pattern.compile(
            "audit\\s*log|audit\\s*logs|root\\s*cause|troubleshoot|investigate|investigation|debug(?:ging)?|what\\s+went\\s+wrong|figure\\s+out",
            Pattern.CASE_INSENSITIVE
    );

    private static final String JIRA_GET_ISSUE = "jira.getJiraIssue";
    private static final String RED_AUDIT_LOG_EXECUTE = "red.red_execute_audit_log_elasticsearch_query";

    public JiraRedAuditToolSelectionSupport() {
    }

    public boolean matchesAuditInvestigationIntent(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return false;
        }
        return AUDIT_INVESTIGATION_PATTERN.matcher(userPrompt).find();
    }

    public boolean containsJiraIssueKey(String userPrompt) {
        if (userPrompt == null || userPrompt.isBlank()) {
            return false;
        }
        return JIRA_KEY_PATTERN.matcher(userPrompt).find();
    }

    public boolean isRedAuditLogTool(String fullyQualifiedToolName) {
        return RED_AUDIT_LOG_EXECUTE.equals(fullyQualifiedToolName);
    }

    /**
     * Returns additional tools to scope when audit investigation intent bridges Jira and RED.
     */
    public List<String> bridgeTools(List<String> primary, String userPrompt, Set<String> availableToolNames) {
        if (userPrompt == null || userPrompt.isBlank() || primary == null || primary.isEmpty()) {
            return List.of();
        }

        boolean auditIntent = matchesAuditInvestigationIntent(userPrompt);
        boolean hasJiraKey = containsJiraIssueKey(userPrompt);
        if (!auditIntent && !hasJiraKey) {
            return List.of();
        }

        boolean primaryHasJira = primary.stream().anyMatch(name -> name.startsWith("jira."));
        boolean primaryHasRedAuditLog = primary.stream().anyMatch(this::isRedAuditLogTool);
        boolean primaryHasGetIssue = primary.contains(JIRA_GET_ISSUE);

        List<String> bridged = new ArrayList<>();
        if (primaryHasRedAuditLog && (hasJiraKey || auditIntent) && availableToolNames.contains(JIRA_GET_ISSUE)) {
            bridged.add(JIRA_GET_ISSUE);
        }
        if ((primaryHasGetIssue || primaryHasJira) && auditIntent
                && availableToolNames.contains(RED_AUDIT_LOG_EXECUTE)) {
            bridged.add(RED_AUDIT_LOG_EXECUTE);
        }
        return dedupePreservingOrder(bridged);
    }

    private static List<String> dedupePreservingOrder(List<String> tools) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> ordered = new ArrayList<>();
        for (String tool : tools) {
            if (seen.add(tool)) {
                ordered.add(tool);
            }
        }
        return ordered;
    }
}
