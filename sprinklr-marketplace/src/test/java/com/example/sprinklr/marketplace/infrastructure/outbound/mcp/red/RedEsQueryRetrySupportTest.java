package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedEsQueryRetrySupportTest {

    private final RedEsQueryRetrySupport support = new RedEsQueryRetrySupport();

    @Test
    void detectsRecoverableParsingException() {
        String body = """
                {"error":{"root_cause":[{"type":"parsing_exception","reason":"[bool] malformed query"}]},"status":400}
                """;
        assertTrue(support.isRecoverableElasticsearchError(body));
    }

    @Test
    void ignoresSuccessfulHits() {
        assertFalse(support.isRecoverableElasticsearchError("{\"hits\":{\"total\":1}}"));
    }

    @Test
    void findsRecoverableExecuteToolFromBatch() {
        Optional<String> tool = support.findRecoverableExecuteTool(
                List.of("red.red_execute_elastic_search_query"),
                List.of(new McpInvocationResult("id", true, """
                        {"error":{"type":"parsing_exception","reason":"malformed query"},"status":400}
                        """, null)));

        assertEquals("red.red_execute_elastic_search_query", tool.orElseThrow());
    }

    @Test
    void buildRetryNudgeMentionsSortAndSize() {
        String nudge = support.buildRetryNudge(null, "red.red_execute_elastic_search_query");
        assertTrue(nudge.contains("sort, size"));
        assertTrue(nudge.contains("red_execute_elastic_search_query"));
    }

    @Test
    void appendRecoverableErrorHintAddsFooter() {
        String error = "{\"error\":{\"type\":\"parsing_exception\"},\"status\":400}";
        String appended = support.appendRecoverableErrorHint(error);
        assertTrue(appended.contains("RECOVERABLE ES ERROR"));
        assertTrue(appended.contains("top-level siblings"));
    }
}
