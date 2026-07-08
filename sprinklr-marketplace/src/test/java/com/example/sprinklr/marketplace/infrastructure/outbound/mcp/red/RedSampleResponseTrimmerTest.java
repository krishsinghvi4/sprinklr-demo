package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedSampleResponseTrimmerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void trimsElasticsearchHitsToLimit() throws Exception {
        String sample = """
                {"hits":{"hits":[
                  {"_source":{"id":1}},
                  {"_source":{"id":2}},
                  {"_source":{"id":3}}
                ]}}
                """;

        String trimmed = RedSampleResponseTrimmer.trimToLimit(sample, 2);

        assertEquals(2, OBJECT_MAPPER.readTree(trimmed).path("hits").path("hits").size());
        assertEquals(2, RedSampleResponseTrimmer.countDocuments(trimmed));
    }

    @Test
    void trimsMongoDocumentsToLimit() throws Exception {
        String sample = """
                {"documents":[{"a":1},{"a":2},{"a":3}]}
                """;

        String trimmed = RedSampleResponseTrimmer.trimToLimit(sample, 2);

        assertEquals(2, OBJECT_MAPPER.readTree(trimmed).path("documents").size());
    }

    @Test
    void leavesContentUnchangedWhenUnderLimit() {
        String sample = "{\"hits\":{\"hits\":[{\"_source\":{\"id\":1}}]}}";

        String trimmed = RedSampleResponseTrimmer.trimToLimit(sample, 2);

        assertEquals(sample, trimmed);
    }

    @Test
    void trimsRootArrayToLimit() throws Exception {
        String sample = "[{\"a\":1},{\"a\":2},{\"a\":3}]";

        String trimmed = RedSampleResponseTrimmer.trimToLimit(sample, 2);

        assertEquals(2, OBJECT_MAPPER.readTree(trimmed).size());
    }

    @Test
    void enrichMongoInjectsLimitAndSortDefaults() throws Exception {
        String enriched = RedSampleQueryArgsEnricher.enrich(
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                """
                        {"partnerId":190,"serverType":"PAID","queryType":"general","collectionName":"paidInitiative"}
                        """,
                2
        );

        var node = OBJECT_MAPPER.readTree(enriched);
        assertEquals(2, node.path("limit").asInt());
        assertEquals("createdTime", node.path("sortField").asText());
        assertEquals("desc", node.path("sortDirection").asText());
    }

    @Test
    void enrichMongoPreservesExistingSort() throws Exception {
        String enriched = RedSampleQueryArgsEnricher.enrich(
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                """
                        {"partnerId":190,"collectionName":"paidInitiative","sortField":"mTm","sortDirection":"asc"}
                        """,
                2
        );

        var node = OBJECT_MAPPER.readTree(enriched);
        assertEquals("mTm", node.path("sortField").asText());
        assertEquals("asc", node.path("sortDirection").asText());
        assertTrue(node.has("limit"));
    }
}
