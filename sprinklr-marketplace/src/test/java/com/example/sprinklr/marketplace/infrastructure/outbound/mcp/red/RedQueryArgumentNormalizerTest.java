package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedQueryArgumentNormalizerTest {

    private final RedEsSampleFieldContext sampleFieldContext = new RedEsSampleFieldContext();
    private final RedQueryArgumentNormalizer normalizer = new RedQueryArgumentNormalizer(sampleFieldContext);

    @BeforeEach
    void setAudienceSampleCatalog() {
        sampleFieldContext.set(new RedEsSampleFieldCatalog(
                Set.of("audience.channelTypes", "createdAt"),
                Set.of("audience.channelTypes")));
    }

    @AfterEach
    void clearContext() {
        sampleFieldContext.clear();
    }

    @Test
    void supportsRedElasticsearchExecuteTool() {
        assertTrue(normalizer.supports("red.red_execute_elastic_search_query"));
        assertTrue(normalizer.supports("red_execute_elastic_search_query"));
        assertTrue(normalizer.supports("red.red_execute_audit_log_elasticsearch_query"));
        assertTrue(normalizer.supports("red_execute_audit_log_elasticsearch_query"));
        assertFalse(normalizer.supports("red.red_sample_elasticsearch_query"));
        assertFalse(normalizer.supports("red.red_execute_mongo_query"));
    }

    @Test
    void stringifiesQueryObject() {
        String input = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIT_LOG",
                  "searchType": "SEARCH",
                  "indexName": "audit_log_p190*",
                  "query": {"query":{"term":{"assetId":"abc"}}}
                }
                """;

        String normalized = normalizer.normalize("red_execute_elastic_search_query", input);

        assertTrue(normalized.contains("\"query\":\"{\\\"query\\\":{\\\"term\\\":{\\\"assetId\\\":\\\"abc\\\"}}}\""));
    }

    @Test
    void leavesStringQueryUnchangedWithoutSampleCatalog() {
        sampleFieldContext.clear();
        String input = """
                {
                  "partnerId": 190,
                  "query": "{\\"query\\":{\\"match_all\\":{}}}"
                }
                """;

        String normalized = normalizer.normalize("red_execute_elastic_search_query", input);

        assertEquals(input.replaceAll("\\s+", ""), normalized.replaceAll("\\s+", ""));
    }

    @Test
    void correctsFilterFieldsUsingSampleCatalogFromCurrentTurn() {
        String input = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIENCE_CONTAINER",
                  "searchType": "SEARCH",
                  "indexName": "audience_container_190*",
                  "query": "{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"channelTypes.keyword\\":\\"FACEBOOK\\"}}]}},\\"sort\\":[{\\"createdAt\\":{\\"order\\":\\"desc\\"}}],\\"size\\":5}"
                }
                """;

        String normalized = normalizer.normalize("red_execute_elastic_search_query", input);

        assertTrue(normalized.contains("audience.channelTypes"));
        assertTrue(normalized.contains("\\\"terms\\\""));
        assertFalse(normalized.contains("channelTypes.keyword"));
    }

    @Test
    void wrapsBareBoolQueryUnderTopLevelQueryKey() {
        sampleFieldContext.clear();
        String input = """
                {
                  "username": "test",
                  "partnerId": 190,
                  "env": "prod16",
                  "query": "{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"assetId\\":\\"abc123\\"}}]}}"
                }
                """;

        String normalized = normalizer.normalize("red_execute_audit_log_elasticsearch_query", input);

        assertTrue(normalized.contains("\\\"query\\\":{\\\"bool\\\""));
    }

    @Test
    void wrapQueryBodyIfNeededLeavesAlreadyWrappedQueryUnchanged() {
        String wrapped = "{\"query\":{\"bool\":{\"must\":[]}}}";
        assertEquals(wrapped, RedQueryArgumentNormalizer.wrapQueryBodyIfNeeded(wrapped));
    }

    @Test
    void wrapQueryBodyIfNeededWrapsBareBoolBody() {
        String bare = "{\"bool\":{\"must\":[{\"term\":{\"assetId\":\"abc\"}}]}}";
        String result = RedQueryArgumentNormalizer.wrapQueryBodyIfNeeded(bare);
        assertTrue(result.contains("\"query\""));
        assertTrue(result.contains("\"bool\""));
    }

    @Test
    void hoistNonQueryClausesMovesSortAndSizeOutOfQueryObject() {
        String malformed = """
                {"query":{"bool":{"must":[{"term":{"accountId":2085979}},{"term":{"entityType":"AD_SET"}}]},"sort":[{"createdTime":{"order":"desc"}}],"size":1}}
                """;

        String hoisted = RedQueryArgumentNormalizer.hoistNonQueryClausesFromQueryObject(malformed);

        assertTrue(hoisted.contains("\"sort\":[{\"createdTime\""));
        assertTrue(hoisted.contains("\"size\":1"));
        assertFalse(hoisted.contains("\"sort\":[{\"createdTime\":{\"order\":\"desc\"}}],\"size\":1}}"));
    }

    @Test
    void hoistNonQueryClausesLeavesCorrectBodyUnchanged() {
        String correct = """
                {"query":{"bool":{"must":[{"term":{"accountId":2085979}}]}},"sort":[{"createdTime":{"order":"desc"}}],"size":1}
                """;

        assertEquals(
                correct.replaceAll("\\s+", ""),
                RedQueryArgumentNormalizer.hoistNonQueryClausesFromQueryObject(correct).replaceAll("\\s+", ""));
    }

    @Test
    void normalizerHoistsNestedSortAndSizeOnExecuteTool() {
        sampleFieldContext.clear();
        String input = """
                {
                  "partnerId": 190,
                  "serverType": "AD_ENTITY",
                  "searchType": "SEARCH",
                  "indexName": "ad_entity_190*",
                  "query": "{\\"query\\":{\\"bool\\":{\\"must\\":[{\\"term\\":{\\"accountId\\":2085979}},{\\"term\\":{\\"entityType\\":\\"AD_SET\\"}}]}},\\"sort\\":[{\\"createdTime\\":{\\"order\\":\\"desc\\"}}],\\"size\\":1}"
                }
                """;

        String normalized = normalizer.normalize("red_execute_elastic_search_query", input);

        assertTrue(normalized.contains("\\\"sort\\\":[{\\\"createdTime\\\""));
        assertTrue(normalized.contains("\\\"size\\\":1"));
    }
}
