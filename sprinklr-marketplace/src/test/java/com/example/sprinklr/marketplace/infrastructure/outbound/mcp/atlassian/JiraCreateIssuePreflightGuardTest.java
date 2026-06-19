package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraCreateIssuePreflightGuardTest {

    private JiraCreateIssuePreflightGuard guard;

    @BeforeEach
    void setUp() {
        guard = new JiraCreateIssuePreflightGuard();
    }

    @Test
    void blocksWhenUserDidNotSpecifyComponent() {
        String args = """
                {
                  "projectKey": "ITOPS",
                  "additional_fields": {
                    "components": [{"name": "support-itops-request"}],
                    "customfield_14371": {"value": "prod"}
                  }
                }
                """;
        var result = guard.validate("createJiraIssue", args, "create a task called testing again");
        assertFalse(result.allowed());
    }

    @Test
    void allowsWhenUserSpecifiedComponentAndCustomField() {
        String args = """
                {
                  "projectKey": "ITOPS",
                  "additional_fields": {
                    "components": [{"name": "support-itops-request"}],
                    "customfield_14371": {"value": "prod"}
                  }
                }
                """;
        var result = guard.validate(
                "jira.createJiraIssue",
                args,
                "create ITOPS task, component support-itops-request, environment prod"
        );
        assertTrue(result.allowed());
    }

    @Test
    void ignoresNonCreateTools() {
        var result = guard.validate("getJiraIssueTypeMetaWithFields", "{}", "anything");
        assertTrue(result.allowed());
    }

    @Test
    void blocksOnMalformedArgumentsJson() {
        var result = guard.validate("createJiraIssue", "{not-json", "create a ticket");
        assertFalse(result.allowed());
    }
}
