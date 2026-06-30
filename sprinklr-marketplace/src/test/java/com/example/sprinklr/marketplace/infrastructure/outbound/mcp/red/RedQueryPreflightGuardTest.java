package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.RedQueryToolSelectionSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedQueryPreflightGuardTest {

    private final RedQueryPreflightGuard guard = new RedQueryPreflightGuard(new RedQueryToolSelectionSupport());

    @Test
    void blocksMongoExecuteWhenSampleNotCalledThisTurn() {
        McpInvocation invocation = new McpInvocation(
                "conn-red", "red_execute_mongo_query", "{}", "call-1");

        PreflightResult result = guard.validate(invocation, "user prompt\n[tools-called-this-turn: ]");

        assertFalse(result.allowed());
    }

    @Test
    void allowsMongoExecuteAfterSampleCalledThisTurn() {
        McpInvocation invocation = new McpInvocation(
                "conn-red", "red_execute_mongo_query", "{}", "call-1");

        PreflightResult result = guard.validate(
                invocation,
                "user prompt\n[tools-called-this-turn: red.red_sample_mongo_query]");

        assertTrue(result.allowed());
    }

    @Test
    void blocksExecuteWhenSampleOnlyMentionedInPriorTurnText() {
        McpInvocation invocation = new McpInvocation(
                "conn-red", "red_execute_elastic_search_query", "{}", "call-1");

        PreflightResult result = guard.validate(
                invocation,
                """
                previous assistant mentioned red_sample_elasticsearch_query
                fetch facebook audiences
                [tools-called-this-turn: red.red_execute_elastic_search_query]""");

        assertFalse(result.allowed());
    }

    @Test
    void allowsElasticsearchExecuteAfterSampleCalledThisTurn() {
        McpInvocation invocation = new McpInvocation(
                "conn-red", "red_execute_elastic_search_query", "{}", "call-1");

        PreflightResult result = guard.validate(
                invocation,
                "user prompt\n[tools-called-this-turn: red.red_sample_elasticsearch_query, red.red_execute_elastic_search_query]");

        assertTrue(result.allowed());
    }

    @Test
    void allowsNonRedTools() {
        PreflightResult result = guard.validate(
                new McpInvocation("conn-jira", "searchJiraIssuesUsingJql", "{}", "call-1"),
                "prompt");

        assertTrue(result.allowed());
    }
}
