package com.example.sprinklr.marketplace.domain.model;

import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolDependencyGraphTest {

    @Test
    void transitivePrerequisitesAreTopologicallyOrdered() {
        // createIssue -> getMeta -> getResources : prerequisites must come before their dependents.
        ToolDependencyGraph graph = new ToolDependencyGraph(
                "jira",
                Map.of(
                        "jira.createIssue", List.of("jira.getMeta"),
                        "jira.getMeta", List.of("jira.getResources")
                ),
                "fp",
                Instant.now(),
                DependencyGraphStatus.READY
        );

        List<String> prerequisites = graph.transitivePrerequisites("jira.createIssue");

        assertEquals(List.of("jira.getResources", "jira.getMeta"), prerequisites);
    }

    @Test
    void toolWithoutPrerequisitesReturnsEmpty() {
        ToolDependencyGraph graph = new ToolDependencyGraph(
                "jira", Map.of(), "fp", Instant.now(), DependencyGraphStatus.READY);

        assertTrue(graph.transitivePrerequisites("jira.searchIssues").isEmpty());
    }
}
