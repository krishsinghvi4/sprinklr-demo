package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraChangelogFormatterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void summarizesStatusAssigneeAndPriorityChangesInChronologicalOrder() throws Exception {
        String raw = """
                {
                  "total": 2,
                  "histories": [
                    {
                      "created": "2024-02-02T10:00:00.000+0000",
                      "author": { "displayName": "Bob" },
                      "items": [
                        { "field": "status", "fieldtype": "jira", "fromString": "In Review", "toString": "Done" }
                      ]
                    },
                    {
                      "created": "2024-02-01T10:00:00.000+0000",
                      "author": { "displayName": "Alice" },
                      "items": [
                        { "field": "status", "fieldtype": "jira", "fromString": "Backlog", "toString": "In Review" },
                        { "field": "assignee", "fieldtype": "jira", "fromString": null, "toString": "Alice" },
                        { "field": "priority", "fieldtype": "jira", "fromString": "Low", "toString": "High" }
                      ]
                    }
                  ]
                }
                """;

        String summary = JiraChangelogFormatter.summarize("ITOPS-1", OBJECT_MAPPER.readTree(raw));

        assertTrue(summary.contains("\"issueKey\" : \"ITOPS-1\""));
        assertTrue(summary.contains("\"field\" : \"status\""));
        assertTrue(summary.contains("\"from\" : \"Backlog\""));
        assertTrue(summary.contains("\"to\" : \"In Review\""));
        assertTrue(summary.contains("\"field\" : \"assignee\""));
        assertTrue(summary.contains("\"field\" : \"priority\""));
        int backlogIndex = summary.indexOf("Backlog");
        int doneIndex = summary.indexOf("Done");
        assertTrue(backlogIndex < doneIndex, "Expected chronological ordering oldest first");
    }
}
