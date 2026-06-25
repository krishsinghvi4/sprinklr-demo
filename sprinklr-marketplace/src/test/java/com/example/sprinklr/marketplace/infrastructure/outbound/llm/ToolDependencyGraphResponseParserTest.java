package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDependencyGraphResponseParserTest {

    private final ToolDependencyGraphResponseParser parser = new ToolDependencyGraphResponseParser();
    private final Set<String> validNames = Set.of(
            "jira.createJiraIssue", "jira.getMeta", "jira.getAccessibleResources");

    @Test
    void parsesValidEdges() {
        String content = "{\"edges\":{\"jira.createJiraIssue\":[\"jira.getMeta\"]}}";

        Map<String, List<String>> edges = parser.parse(content, validNames);

        assertEquals(List.of("jira.getMeta"), edges.get("jira.createJiraIssue"));
    }

    @Test
    void stripsMarkdownFences() {
        String content = "```json\n{\"edges\":{\"jira.createJiraIssue\":[\"jira.getMeta\"]}}\n```";

        Map<String, List<String>> edges = parser.parse(content, validNames);

        assertEquals(List.of("jira.getMeta"), edges.get("jira.createJiraIssue"));
    }

    @Test
    void acceptsBareEdgesObjectWithoutWrapper() {
        String content = "{\"jira.createJiraIssue\":[\"jira.getMeta\"]}";

        Map<String, List<String>> edges = parser.parse(content, validNames);

        assertEquals(List.of("jira.getMeta"), edges.get("jira.createJiraIssue"));
    }

    @Test
    void omitsEmptyPrerequisiteArrays() {
        String content = "{\"edges\":{\"jira.getMeta\":[]}}";

        Map<String, List<String>> edges = parser.parse(content, validNames);

        assertTrue(edges.isEmpty());
    }

    @Test
    void rejectsUnknownPrerequisite() {
        String content = "{\"edges\":{\"jira.createJiraIssue\":[\"jira.unknownTool\"]}}";

        assertThrows(ToolDependencyGraphResponseParser.GraphParseException.class,
                () -> parser.parse(content, validNames));
    }

    @Test
    void rejectsCycle() {
        String content = "{\"edges\":{"
                + "\"jira.createJiraIssue\":[\"jira.getMeta\"],"
                + "\"jira.getMeta\":[\"jira.createJiraIssue\"]}}";

        assertThrows(ToolDependencyGraphResponseParser.GraphParseException.class,
                () -> parser.parse(content, validNames));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ToolDependencyGraphResponseParser.GraphParseException.class,
                () -> parser.parse("not json", validNames));
    }
}
