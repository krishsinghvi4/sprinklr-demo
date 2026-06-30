package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.RedQueryToolSelectionSupport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedQueryWorkflowSupportTest {

    private final RedQueryWorkflowSupport workflowSupport =
            new RedQueryWorkflowSupport(new RedQueryToolSelectionSupport());

    @Test
    void detectsPendingExecuteAfterSampleRan() {
        List<McpTool> scoped = List.of(
                tool("red.red_sample_elasticsearch_query"),
                tool("red.red_execute_elastic_search_query"));

        assertEquals(
                "red.red_execute_elastic_search_query",
                workflowSupport.pendingExecuteAfterSample(
                        scoped,
                        List.of("red.red_sample_elasticsearch_query")).orElseThrow());
    }

    @Test
    void emptyWhenExecuteAlreadyRan() {
        List<McpTool> scoped = List.of(
                tool("red.red_sample_elasticsearch_query"),
                tool("red.red_execute_elastic_search_query"));

        assertTrue(workflowSupport.pendingExecuteAfterSample(
                scoped,
                List.of("red.red_sample_elasticsearch_query", "red.red_execute_elastic_search_query")).isEmpty());
    }

    @Test
    void ensureExecuteToolInScopeAddsMissingExecuteAfterSample() {
        List<McpTool> scoped = new java.util.ArrayList<>(List.of(
                tool("red.red_sample_elasticsearch_query")));
        List<McpTool> all = List.of(
                tool("red.red_sample_elasticsearch_query"),
                tool("red.red_execute_elastic_search_query"));

        workflowSupport.ensureExecuteToolInScope(
                scoped, all, List.of("red.red_sample_elasticsearch_query"));

        assertEquals(2, scoped.size());
        assertEquals("red.red_execute_elastic_search_query", scoped.get(1).name());
    }

    private static McpTool tool(String name) {
        return new McpTool(name, "desc", "conn", "{}");
    }
}
