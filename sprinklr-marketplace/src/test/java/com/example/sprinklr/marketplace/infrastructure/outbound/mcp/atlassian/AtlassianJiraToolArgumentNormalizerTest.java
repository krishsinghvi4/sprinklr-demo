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
    void hoistsComponentsAndWrapsSelectCustomFieldValues() throws Exception {
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

        assertTrue(root.get("components").isArray());
        assertEquals("API", root.get("components").get(0).asText());
        assertFalse(root.get("additional_fields").has("components"));
        assertEquals("All", root.path("additional_fields").path("customfield_10053").path("value").asText());
    }

    @Test
    void leavesUnrelatedToolsUntouched() {
        String args = "{\"query\":\"assignee = currentUser()\"}";
        assertEquals(args, normalizer.normalize("searchJiraIssuesUsingJql", args));
    }
}
