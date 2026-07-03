package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraRedAuditToolSelectionSupportTest {

    private final JiraRedAuditToolSelectionSupport support = new JiraRedAuditToolSelectionSupport();

    @Test
    void bridgesJiraGetIssueWhenRedAuditToolSelectedWithTicketKey() {
        Set<String> available = Set.of(
                "jira.getJiraIssue",
                "red.red_execute_audit_log_elasticsearch_query"
        );

        List<String> bridged = support.bridgeTools(
                List.of("red.red_execute_audit_log_elasticsearch_query"),
                "check audit logs for ABC-123",
                available
        );

        assertTrue(bridged.contains("jira.getJiraIssue"));
    }

    @Test
    void bridgesRedAuditToolWhenGetIssueSelectedWithAuditIntent() {
        Set<String> available = new LinkedHashSet<>(Set.of(
                "jira.getJiraIssue",
                "red.red_execute_audit_log_elasticsearch_query"
        ));

        List<String> bridged = support.bridgeTools(
                List.of("jira.getJiraIssue"),
                "debug root cause from audit logs for ABC-123",
                available
        );

        assertTrue(bridged.contains("red.red_execute_audit_log_elasticsearch_query"));
    }

    @Test
    void doesNotBridgeWithoutAuditIntentOrTicketKey() {
        Set<String> available = Set.of(
                "jira.getJiraIssue",
                "red.red_execute_audit_log_elasticsearch_query"
        );

        List<String> bridged = support.bridgeTools(
                List.of("jira.getJiraIssue"),
                "show ticket summary",
                available
        );

        assertTrue(bridged.isEmpty());
    }

    @Test
    void detectsAuditInvestigationIntent() {
        assertTrue(support.matchesAuditInvestigationIntent("what went wrong in audit logs"));
        assertFalse(support.matchesAuditInvestigationIntent("show my tickets"));
    }
}
