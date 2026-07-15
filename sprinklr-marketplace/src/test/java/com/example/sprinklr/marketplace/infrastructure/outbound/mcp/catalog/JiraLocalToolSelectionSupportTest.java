package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraLocalToolSelectionSupportTest {

    private final JiraLocalToolSelectionSupport support = new JiraLocalToolSelectionSupport();

    @Test
    void expandsChangelogWithAccessibleResourcesPrerequisite() {
        Set<String> available = Set.of(
                "jira.getAccessibleAtlassianResources",
                "jira.getJiraIssueChangelog"
        );

        assertEquals(
                List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.getJiraIssueChangelog"
                ),
                support.prerequisitesFor("jira.getJiraIssueChangelog", available)
        );
    }

    @Test
    void expandsSearchWithLookupWhenPromptHasEmail() {
        Set<String> available = Set.of(
                "jira.getAccessibleAtlassianResources",
                "jira.lookupJiraAccountId",
                "jira.search"
        );
        String prompt = "open tickets for parvat.singh@sprinklr.com";

        assertTrue(support.hasLocalPrerequisites("jira.search", prompt));
        assertEquals(
                List.of(
                        "jira.getAccessibleAtlassianResources",
                        "jira.lookupJiraAccountId",
                        "jira.search"
                ),
                support.prerequisitesFor("jira.search", available, prompt)
        );
    }

    @Test
    void doesNotExpandSearchWhenPromptHasNoEmail() {
        assertFalse(support.hasLocalPrerequisites("jira.search", "my open tickets"));
        assertEquals(
                List.of("jira.search"),
                support.prerequisitesFor("jira.search", Set.of("jira.search"), "my open tickets")
        );
    }
}
