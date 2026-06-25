package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses and validates the raw LLM output of {@code tool-dependency-graph-prompt.txt} into a clean
 * {@code edges} map. Rejects anything that would corrupt the graph so the generator can retry or fail
 * safely: malformed JSON, references to unknown tools, or cycles (the graph must be a DAG).
 */
@Component
public class ToolDependencyGraphResponseParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Thrown when the LLM response cannot be turned into a valid DAG. */
    public static class GraphParseException extends RuntimeException {
        public GraphParseException(String message) {
            super(message);
        }
    }

    /**
     * @param rawContent     raw LLM message content (may be wrapped in markdown fences)
     * @param validToolNames fully-qualified names that are allowed to appear as keys or values
     * @return validated edges (fully-qualified tool name -> direct prerequisites)
     */
    public Map<String, List<String>> parse(String rawContent, Set<String> validToolNames) {
        if (rawContent == null || rawContent.isBlank()) {
            throw new GraphParseException("empty dependency-graph response");
        }
        String json = stripFences(rawContent.trim());

        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(json);
        } catch (Exception e) {
            throw new GraphParseException("malformed JSON: " + e.getMessage());
        }

        JsonNode edgesNode = root.path("edges");
        if (edgesNode.isMissingNode() || edgesNode.isNull()) {
            // Some models omit the wrapper and return the edges object directly.
            edgesNode = root;
        }
        if (!edgesNode.isObject()) {
            throw new GraphParseException("'edges' is not a JSON object");
        }

        Map<String, List<String>> edges = new LinkedHashMap<>();
        edgesNode.fields().forEachRemaining(entry -> {
            String tool = entry.getKey();
            if (!validToolNames.contains(tool)) {
                throw new GraphParseException("unknown tool in edge key: " + tool);
            }
            JsonNode prereqArray = entry.getValue();
            if (!prereqArray.isArray()) {
                throw new GraphParseException("prerequisites for " + tool + " must be an array");
            }
            List<String> prerequisites = new ArrayList<>();
            for (JsonNode prereq : prereqArray) {
                String name = prereq.asText();
                if (!validToolNames.contains(name)) {
                    throw new GraphParseException("unknown prerequisite tool: " + name);
                }
                if (name.equals(tool)) {
                    throw new GraphParseException("tool cannot depend on itself: " + tool);
                }
                if (!prerequisites.contains(name)) {
                    prerequisites.add(name);
                }
            }
            if (!prerequisites.isEmpty()) {
                edges.put(tool, prerequisites);
            }
        });

        assertNoCycle(edges);
        return edges;
    }

    private void assertNoCycle(Map<String, List<String>> edges) {
        Set<String> visited = new HashSet<>();
        Set<String> onPath = new HashSet<>();
        for (String node : edges.keySet()) {
            if (hasCycle(node, edges, visited, onPath)) {
                throw new GraphParseException("dependency cycle detected at: " + node);
            }
        }
    }

    private boolean hasCycle(String node, Map<String, List<String>> edges, Set<String> visited, Set<String> onPath) {
        if (onPath.contains(node)) {
            return true;
        }
        if (!visited.add(node)) {
            return false;
        }
        onPath.add(node);
        for (String next : edges.getOrDefault(node, List.of())) {
            if (hasCycle(next, edges, visited, onPath)) {
                return true;
            }
        }
        onPath.remove(node);
        return false;
    }

    private String stripFences(String text) {
        String stripped = text;
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline >= 0) {
                stripped = stripped.substring(firstNewline + 1);
            }
            int closingFence = stripped.lastIndexOf("```");
            if (closingFence >= 0) {
                stripped = stripped.substring(0, closingFence);
            }
        }
        return stripped.trim();
    }
}
