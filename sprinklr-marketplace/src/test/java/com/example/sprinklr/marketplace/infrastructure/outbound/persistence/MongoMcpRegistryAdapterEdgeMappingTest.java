package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.DependencyGraphStatus;
import com.example.sprinklr.marketplace.domain.model.ToolDependencyGraph;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoMcpRegistryAdapterEdgeMappingTest {

    @Test
    void roundTripsDottedToolNamesThroughEdgeDocuments() throws Exception {
        MongoMcpRegistryAdapter adapter = new MongoMcpRegistryAdapter(null);
        Map<String, List<String>> edges = Map.of(
                "gitlab.list_pipelines", List.of("gitlab.get_project"),
                "jira.createJiraIssue", List.of("jira.getAccessibleAtlassianResources")
        );

        List<EdgeDocument> documents = (List<EdgeDocument>) invoke(adapter, "toEdgeDocuments", Map.class, edges);
        assertEquals(2, documents.size());
        assertTrue(documents.stream().anyMatch(d -> "gitlab.list_pipelines".equals(d.tool())));

        Map<String, List<String>> restored = (Map<String, List<String>>) invoke(adapter, "toEdgeMap", List.class, documents);
        assertEquals(edges, restored);
    }

    @Test
    void updateDependencyGraphUsesListEdges() throws Exception {
        ToolDependencyGraph graph = new ToolDependencyGraph(
                "gitlab",
                Map.of("gitlab.list_pipelines", List.of("gitlab.get_project")),
                "fp",
                Instant.now(),
                DependencyGraphStatus.READY
        );
        MongoMcpRegistryAdapter adapter = new MongoMcpRegistryAdapter(null);

        List<EdgeDocument> documents = (List<EdgeDocument>) invoke(adapter, "toEdgeDocuments", Map.class, graph.edges());
        ToolDependencyGraphDocument document = new ToolDependencyGraphDocument(documents, graph.toolsFingerprint(),
                graph.generatedAt());

        assertEquals(1, document.edges().size());
        assertEquals("gitlab.list_pipelines", document.edges().get(0).tool());
    }

    private Object invoke(Object target, String methodName, Class<?> paramType, Object arg) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, paramType);
        method.setAccessible(true);
        return method.invoke(target, arg);
    }
}
