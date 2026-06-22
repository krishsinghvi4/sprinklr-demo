package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraIssueTypeMetadataProcessorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void summarize_listsAllTopLevelRequiredFieldsFromObjectMap() throws Exception {
        String raw = """
                {
                  "fields": {
                    "summary": {
                      "fieldId": "summary",
                      "name": "Summary",
                      "required": true,
                      "schema": { "type": "string", "system": "summary" }
                    },
                    "customfield_10001": {
                      "fieldId": "customfield_10001",
                      "key": "customfield_10001",
                      "name": "Severity",
                      "required": true,
                      "allowedValues": [
                        { "value": "Blocker" },
                        { "value": "Trivial" }
                      ]
                    },
                    "description": {
                      "fieldId": "description",
                      "name": "Description",
                      "required": false
                    }
                  }
                }
                """;

        var processed = JiraIssueTypeMetadataProcessor.analyzeSinglePage(MAPPER.readTree(raw));
        String summary = JiraIssueTypeMetadataProcessor.summarize(processed).orElseThrow();

        JsonNode summaryNode = MAPPER.readTree(summary);
        assertEquals("jiraIssueTypeCreateFieldsSummary", summaryNode.path("metadataKind").asText());
        assertEquals(2, summaryNode.path("requiredFieldCount").asInt());
        assertEquals(2, summaryNode.path("requiredUserInputCount").asInt());
        assertEquals("customfield_10001", summaryNode.path("requiredForCreate").get(1).path("additionalFieldsKey").asText());
        assertEquals("additional_fields", summaryNode.path("requiredForCreate").get(1).path("placement").asText());
        assertEquals("top_level", summaryNode.path("requiredForCreate").get(0).path("placement").asText());
        assertTrue(summary.contains("Summary"));
        assertTrue(summary.contains("Severity"));
        assertTrue(summary.contains("Trivial"));
    }

    @Test
    void mergePages_combinesPaginatedFieldArraysAndFindsRequiredOnLaterPage() throws Exception {
        JsonNode page1 = MAPPER.readTree("""
                {
                  "startAt": 0,
                  "maxResults": 1,
                  "total": 2,
                  "fields": [
                    {
                      "fieldId": "summary",
                      "name": "Summary",
                      "required": true
                    }
                  ]
                }
                """);
        JsonNode page2 = MAPPER.readTree("""
                {
                  "startAt": 1,
                  "maxResults": 1,
                  "total": 2,
                  "fields": [
                    {
                      "fieldId": "customfield_20002",
                      "name": "Fix versions",
                      "required": true,
                      "allowedValues": [{ "name": "Release_Backlog" }]
                    }
                  ]
                }
                """);

        var processed = JiraIssueTypeMetadataProcessor.mergePages(page1, List.of(page2));
        String summary = JiraIssueTypeMetadataProcessor.summarize(processed).orElseThrow();

        assertEquals(2, processed.requiredFieldCount());
        assertFalse(processed.paginationIncomplete());
        assertTrue(summary.contains("Fix versions"));
        assertTrue(summary.contains("Release_Backlog"));
    }

    @Test
    void fieldShapes_marksArrayOptionFieldsAsOptionArray() throws Exception {
        String raw = """
                {
                  "fields": {
                    "customfield_18501": {
                      "fieldId": "customfield_18501",
                      "name": "Functional Solution",
                      "required": true,
                      "schema": { "type": "array", "items": "option" }
                    },
                    "customfield_22858": {
                      "fieldId": "customfield_22858",
                      "name": "Severity",
                      "required": true,
                      "schema": { "type": "option" }
                    }
                  }
                }
                """;
        var processed = JiraIssueTypeMetadataProcessor.analyzeSinglePage(MAPPER.readTree(raw));
        Map<String, String> shapes = JiraIssueTypeMetadataProcessor.fieldShapes(processed);
        assertEquals("option_array", shapes.get("customfield_18501"));
        assertEquals("option_object", shapes.get("customfield_22858"));
    }

    @Test
    void needsMorePages_whenTotalExceedsFetchedFields() throws Exception {
        JsonNode page = MAPPER.readTree("""
                { "total": 120, "startAt": 0, "maxResults": 50, "fields": { "summary": { "required": true, "name": "Summary" } } }
                """);
        assertTrue(JiraIssueTypeMetadataProcessor.needsMorePages(page, 1));
    }

    @Test
    void requiredUserInputFields_returnsAskUserRequiredFieldsOnly() throws Exception {
        String raw = """
                {
                  "fields": {
                    "summary": {
                      "fieldId": "summary",
                      "name": "Summary",
                      "required": true,
                      "schema": { "type": "string", "system": "summary" }
                    },
                    "project": {
                      "fieldId": "project",
                      "name": "Project",
                      "required": true,
                      "schema": { "type": "project", "system": "project" }
                    },
                    "customfield_10001": {
                      "fieldId": "customfield_10001",
                      "name": "Severity",
                      "required": true,
                      "schema": { "type": "option" }
                    }
                  }
                }
                """;
        var processed = JiraIssueTypeMetadataProcessor.analyzeSinglePage(MAPPER.readTree(raw));
        var fields = JiraIssueTypeMetadataProcessor.requiredUserInputFields(processed);

        assertEquals(2, fields.size());
        assertEquals("summary", fields.get(0).fieldId());
        assertEquals("top_level", fields.get(0).placement());
        assertEquals("customfield_10001", fields.get(1).fieldId());
        assertEquals("additional_fields", fields.get(1).placement());
    }

    @Test
    void parseCreateScope_readsProjectIdOrKeyAndIssueTypeId() throws Exception {
        String args = """
                {
                  "projectIdOrKey": "PAID",
                  "issueTypeId": "26"
                }
                """;
        var scope = JiraIssueTypeMetadataProcessor.parseCreateScope(args).orElseThrow();
        assertEquals("PAID", scope.projectKey());
        assertEquals("26", scope.issueTypeId());
    }

    @Test
    void parseCreateScope_readsProjectAndIssueTypeFromArguments() throws Exception {
        String args = """
                {
                  "projectKey": "ITOPS",
                  "issueTypeName": "Story"
                }
                """;
        var scope = JiraIssueTypeMetadataProcessor.parseCreateScope(args).orElseThrow();
        assertEquals("ITOPS", scope.projectKey());
        assertEquals("Story", scope.issueTypeName());
    }
}
