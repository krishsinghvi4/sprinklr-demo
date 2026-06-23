package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlassianJiraToolArgumentNormalizerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final AtlassianJiraToolArgumentNormalizer normalizer = new AtlassianJiraToolArgumentNormalizer();

    @Test
    void keepsComponentsInAdditionalFieldsAndWrapsSelectCustomFieldValues() throws Exception {
        String input = """
                {
                  "cloudId": "sprinklr.atlassian.net",
                  "projectKey": "ITOPS",
                  "issueTypeName": "Task",
                  "summary": "New ITOPS Task",
                  "additional_fields": {
                    "components": [{"name": "API"}],
                    "customfield_10053": "All"
                  }
                }
                """;

        String normalized = normalizer.normalize("createJiraIssue", input);
        JsonNode root = OBJECT_MAPPER.readTree(normalized);

        assertFalse(root.has("components"));
        assertEquals("API", root.path("additional_fields").path("components").path(0).path("name").asText());
        assertEquals("All", root.path("additional_fields").path("customfield_10053").path("value").asText());
    }

    @Test
    void nestsTopLevelComponentIdsIntoAdditionalFields() throws Exception {
        String input = """
                {
                  "cloudId": "sprinklr.atlassian.net",
                  "projectKey": "ITOPS",
                  "issueTypeName": "Task",
                  "summary": "New ITOPS Task",
                  "components": [{"id": "12808"}],
                  "additional_fields": {"customfield_14371": {"value": "dev"}}
                }
                """;

        String normalized = normalizer.normalize("createJiraIssue", input);
        JsonNode root = OBJECT_MAPPER.readTree(normalized);

        assertFalse(root.has("components"));
        assertEquals("12808", root.path("additional_fields").path("components").path(0).path("id").asText());
    }

    @Test
    void nestsTopLevelComponentNameStringsIntoAdditionalFields() throws Exception {
        String input = """
                {
                  "cloudId": "sprinklr.atlassian.net",
                  "projectKey": "ITOPS",
                  "issueTypeName": "Task",
                  "summary": "New ITOPS Task",
                  "components": ["API"]
                }
                """;

        String normalized = normalizer.normalize("createJiraIssue", input);
        JsonNode root = OBJECT_MAPPER.readTree(normalized);

        assertFalse(root.has("components"));
        assertTrue(root.path("additional_fields").path("components").isArray());
        assertEquals("API", root.path("additional_fields").path("components").path(0).path("name").asText());
    }

    @Test
    void supportsPrefixedToolNames() throws Exception {
        String input = """
                {
                  "cloudId": "sprinklr.atlassian.net",
                  "projectKey": "ITOPS",
                  "issueTypeName": "Task",
                  "summary": "New ITOPS Task",
                  "components": ["API"]
                }
                """;

        String normalized = normalizer.normalize("jira.createJiraIssue", input);
        JsonNode root = OBJECT_MAPPER.readTree(normalized);

        assertFalse(root.has("components"));
        assertEquals("API", root.path("additional_fields").path("components").path(0).path("name").asText());
    }

    @Test
    void leavesUnrelatedToolsUntouched() {
        String args = "{\"query\":\"assignee = currentUser()\"}";
        assertEquals(args, normalizer.normalize("searchJiraIssuesUsingJql", args));
    }
}
