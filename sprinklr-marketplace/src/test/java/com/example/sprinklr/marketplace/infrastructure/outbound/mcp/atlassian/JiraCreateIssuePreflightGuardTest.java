package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraCreateIssuePreflightGuardTest {

    private static final String CONNECTION_ID = "conn-jira-1";

    private JiraIssueTypeCreateRequirementsCache requirementsCache;
    private JiraCreateIssuePreflightGuard guard;

    @BeforeEach
    void setUp() {
        requirementsCache = new JiraIssueTypeCreateRequirementsCache();
        guard = new JiraCreateIssuePreflightGuard(requirementsCache);
    }

    @Test
    void allowsWhenRequirementsCachedUnderIssueTypeIdButCreateUsesName() {
        requirementsCache.put(
                CONNECTION_ID,
                "PAID",
                "26",
                List.of(new JiraIssueTypeMetadataProcessor.RequiredField(
                        "summary", "Summary", "top_level", ""
                ))
        );

        String args = """
                {
                  "projectKey": "PAID",
                  "issueTypeName": "Story",
                  "summary": "test"
                }
                """;

        var result = guard.validate(CONNECTION_ID, "createJiraIssue", args);
        assertTrue(result.allowed());
    }

    @Test
    void blocksWhenMetadataNotCached() {
        String args = """
                {
                  "projectKey": "ITOPS",
                  "issueTypeName": "Story",
                  "summary": "Test story"
                }
                """;

        var result = guard.validate(CONNECTION_ID, "createJiraIssue", args);
        assertFalse(result.allowed());
        assertTrue(result.blockMessage().contains("getJiraIssueTypeMetaWithFields"));
    }

    @Test
    void blocksWhenRequiredCustomFieldMissingFromArgs() {
        seedRequirements();

        String args = """
                {
                  "projectKey": "ITOPS",
                  "issueTypeName": "Story",
                  "summary": "Test story",
                  "additional_fields": {
                    "components": [{"name": "support-itops-request"}]
                  }
                }
                """;

        var result = guard.validate(CONNECTION_ID, "jira.createJiraIssue", args);
        assertFalse(result.allowed());
        assertTrue(result.blockMessage().contains("Severity"));
    }

    @Test
    void allowsWhenAllCachedRequiredFieldsPresent() {
        seedRequirements();

        String args = """
                {
                  "projectKey": "ITOPS",
                  "issueTypeName": "Story",
                  "summary": "Test story",
                  "additional_fields": {
                    "components": [{"name": "support-itops-request"}],
                    "customfield_14371": {"value": "prod"}
                  }
                }
                """;

        var result = guard.validate(CONNECTION_ID, "createJiraIssue", args);
        assertTrue(result.allowed());
    }

    @Test
    void ignoresNonCreateTools() {
        var result = guard.validate(CONNECTION_ID, "getJiraIssueTypeMetaWithFields", "{}");
        assertTrue(result.allowed());
    }

    @Test
    void blocksDisplayNameAdditionalFieldKeys() {
        seedRequirements();

        String args = """
                {
                  "projectKey": "ITOPS",
                  "issueTypeName": "Story",
                  "summary": "Test story",
                  "additional_fields": {
                    "Functional Solution": {"value": "AI"},
                    "components": [{"name": "support-itops-request"}],
                    "customfield_14371": {"value": "prod"}
                  }
                }
                """;
        var result = guard.validate(CONNECTION_ID, "createJiraIssue", args);
        assertFalse(result.allowed());
        assertTrue(result.blockMessage().contains("display name"));
    }

    @Test
    void blocksOnMalformedArgumentsJson() {
        var result = guard.validate(CONNECTION_ID, "createJiraIssue", "{not-json");
        assertFalse(result.allowed());
    }

    private void seedRequirements() {
        requirementsCache.put(
                CONNECTION_ID,
                "ITOPS",
                "Story",
                List.of(
                        new JiraIssueTypeMetadataProcessor.RequiredField(
                                "summary", "Summary", "top_level", ""
                        ),
                        new JiraIssueTypeMetadataProcessor.RequiredField(
                                "components", "Components", "additional_fields", "components"
                        ),
                        new JiraIssueTypeMetadataProcessor.RequiredField(
                                "customfield_14371", "Severity", "additional_fields", "customfield_14371"
                        )
                )
        );
    }
}
