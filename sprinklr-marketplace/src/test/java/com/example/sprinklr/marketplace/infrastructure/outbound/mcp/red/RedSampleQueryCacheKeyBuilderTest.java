package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedSampleQueryCacheKeyBuilderTest {

    private RedSampleQueryCacheKeyBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RedSampleQueryCacheKeyBuilder();
    }

    @Test
    void buildsStableKeyForElasticsearchSampleScopeArgs() {
        String args = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIENCE_CONTAINER",
                  "searchType": "SEARCH",
                  "indexName": "audience_container_190*",
                  "query": {"match_all": {}}
                }
                """;

        RedSampleQueryCacheKey first = builder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);
        RedSampleQueryCacheKey second = builder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);

        assertEquals(first.id(), second.id());
        assertTrue(first.scopeArgsJson().contains("\"username\":\"test\""));
        assertTrue(first.scopeArgsJson().contains("\"partnerId\":190"));
        assertTrue(!first.scopeArgsJson().contains("query"));
    }

    @Test
    void defaultsUsernameForElasticsearchWhenAbsent() {
        String withoutUsername = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        String withUsername = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*","username":"test"}
                """;

        RedSampleQueryCacheKey without = builder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, withoutUsername);
        RedSampleQueryCacheKey with = builder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, withUsername);

        assertEquals(without.id(), with.id());
    }

    @Test
    void executeArgsMapToSameKeyAsSampleForMatchingScope() {
        String sampleArgs = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        String executeArgs = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIENCE_CONTAINER",
                  "searchType": "SEARCH",
                  "indexName": "audience_container_190*",
                  "query": "{\\"query\\":{\\"match_all\\":{}}}"
                }
                """;

        RedSampleQueryCacheKey sampleKey = builder.build(
                "user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, sampleArgs);
        RedSampleQueryCacheKey executeKey = builder.build(
                "user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL, executeArgs);

        assertEquals(sampleKey.id(), executeKey.id());
    }

    @Test
    void mongoSampleIgnoresQueryAndLimit() {
        String args = """
                {
                  "partnerId": 42,
                  "collectionName": "audiences",
                  "queryType": "general",
                  "serverType": "PAID",
                  "query": {"status":"ACTIVE"},
                  "limit": 10
                }
                """;

        RedSampleQueryCacheKey key = builder.build(
                "user-1", "conn-1", RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL, args);

        assertTrue(key.scopeArgsJson().contains("\"collectionName\":\"audiences\""));
        assertTrue(!key.scopeArgsJson().contains("\"query\""));
        assertTrue(!key.scopeArgsJson().contains("\"limit\""));
    }

    @Test
    void differentUsersProduceDifferentKeys() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;

        RedSampleQueryCacheKey userOne = builder.build(
                "user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);
        RedSampleQueryCacheKey userTwo = builder.build(
                "user-2", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);

        assertNotEquals(userOne.id(), userTwo.id());
    }
}
