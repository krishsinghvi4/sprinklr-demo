package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
