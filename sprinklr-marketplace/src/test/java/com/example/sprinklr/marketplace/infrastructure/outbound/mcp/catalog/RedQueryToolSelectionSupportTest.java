package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RedQueryToolSelectionSupportTest {

    private final RedQueryToolSelectionSupport support = new RedQueryToolSelectionSupport();

    @Test
    void expandsExecutePrimaryToSampleThenExecute() {
        assertEquals(
                List.of("red.red_sample_mongo_query", "red.red_execute_mongo_query"),
                support.expandQueryPair(
                        "red.red_execute_mongo_query",
                        Set.of("red.red_sample_mongo_query", "red.red_execute_mongo_query")));
    }

    @Test
    void expandsSamplePrimaryToSampleThenExecute() {
        assertEquals(
                List.of("red.red_sample_elasticsearch_query", "red.red_execute_elastic_search_query"),
                support.expandQueryPair(
                        "red.red_sample_elasticsearch_query",
                        Set.of("red.red_sample_elasticsearch_query", "red.red_execute_elastic_search_query")));
    }
}
