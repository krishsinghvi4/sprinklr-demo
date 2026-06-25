package com.example.sprinklr.marketplace.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-MCP-server tool dependency graph, generated once by an LLM when a user connects a server.
 * <p>
 * {@code edges} maps a fully-qualified tool name (e.g. {@code "jira.createJiraIssue"}) to the list of
 * tools that MUST run before it in the same turn (its direct, ordered prerequisites). The graph is a
 * DAG; transitive prerequisites are resolved on demand via {@link #transitivePrerequisites(String)}.
 * <p>
 * One graph belongs to exactly one connection ({@code serverIdPrefix} scopes it), so a Jira graph can
 * never pull in GitLab tools and vice-versa.
 */
public record ToolDependencyGraph(
        String serverIdPrefix,
        Map<String, List<String>> edges,
        String toolsFingerprint,
        Instant generatedAt,
        DependencyGraphStatus status
) {

    public ToolDependencyGraph {
        if (serverIdPrefix == null || serverIdPrefix.isBlank()) {
            throw new IllegalArgumentException("ToolDependencyGraph serverIdPrefix must not be blank");
        }
        edges = edges == null ? Map.of() : Map.copyOf(edges);
        status = status == null ? DependencyGraphStatus.PENDING : status;
    }

    /** Direct prerequisites declared for {@code toolName} (empty when none). */
    public List<String> directPrerequisites(String toolName) {
        return edges.getOrDefault(toolName, List.of());
    }

    /**
     * Returns all prerequisites of {@code toolName} (transitive closure) in topological order:
     * a tool always appears after the tools it depends on. The requested tool itself is excluded.
     */
    public List<String> transitivePrerequisites(String toolName) {
        List<String> ordered = new ArrayList<>();
        collect(toolName, new HashSet<>(), ordered);
        return ordered;
    }

    private void collect(String toolName, Set<String> inProgress, List<String> out) {
        if (!inProgress.add(toolName)) {
            return; // defensive: ignore cycles even though the graph is validated as a DAG
        }
        for (String prerequisite : directPrerequisites(toolName)) {
            collect(prerequisite, inProgress, out);
            if (!out.contains(prerequisite)) {
                out.add(prerequisite);
            }
        }
        inProgress.remove(toolName);
    }
}
