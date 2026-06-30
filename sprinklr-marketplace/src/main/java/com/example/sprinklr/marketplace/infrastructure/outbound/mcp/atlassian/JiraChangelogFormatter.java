package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Formats Jira REST changelog JSON into a compact timeline for the LLM.
 */
public final class JiraChangelogFormatter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JiraChangelogFormatter() {
    }

    public static String summarize(String issueKey, JsonNode changelogRoot) {
        ObjectNode summary = OBJECT_MAPPER.createObjectNode();
        summary.put("issueKey", issueKey);
        summary.put("total", changelogRoot.path("total").asInt(0));

        ArrayNode histories = OBJECT_MAPPER.createArrayNode();
        JsonNode historiesNode = changelogRoot.path("histories");
        if (historiesNode.isArray()) {
            List<JsonNode> sorted = new ArrayList<>();
            historiesNode.forEach(sorted::add);
            sorted.sort((left, right) -> left.path("created").asText("").compareTo(right.path("created").asText("")));
            for (JsonNode history : sorted) {
                histories.add(toHistoryEntry(history));
            }
        }
        summary.set("histories", histories);

        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (Exception exception) {
            return summary.toString();
        }
    }

    private static ObjectNode toHistoryEntry(JsonNode history) {
        ObjectNode entry = OBJECT_MAPPER.createObjectNode();
        entry.put("created", history.path("created").asText(""));
        entry.put("author", history.path("author").path("displayName").asText("Unknown"));

        ArrayNode changes = OBJECT_MAPPER.createArrayNode();
        JsonNode items = history.path("items");
        if (items.isArray()) {
            for (JsonNode item : items) {
                ObjectNode change = OBJECT_MAPPER.createObjectNode();
                change.put("field", item.path("field").asText(""));
                change.put("fieldType", item.path("fieldtype").asText(""));
                change.put("from", item.path("fromString").asText(null));
                change.put("to", item.path("toString").asText(null));
                changes.add(change);
            }
        }
        entry.set("changes", changes);
        return entry;
    }
}
