package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedSampleFieldPathIndexTest {

    @Test
    void extractsAudienceContainerPathsWithFlattenedObjectArrays() {
        String sample = """
                {
                  "hits": {
                    "hits": [
                      {
                        "_index": "audience_container_66000000",
                        "_id": "doc-1",
                        "_score": 1.0,
                        "_source": {
                          "lcName": "Test Audience",
                          "additionalInformation": {
                            "delayReason": "NONE"
                          },
                          "audience": {
                            "notifyOnCompletion": true,
                            "segmentType": "STANDARD",
                            "status": "PROCESSED",
                            "channelTypes": ["FACEBOOK"],
                            "filterGroups": [
                              {
                                "segmentId": "65b934d4edcf3b5bbee4fc97",
                                "appId": "TWITTER_FIREHOSE",
                                "excluded": false,
                                "audienceFilters": [],
                                "filtersToProcess": []
                              },
                              {
                                "segmentId": "other-segment-id",
                                "appId": "FACEBOOK",
                                "excluded": true
                              }
                            ]
                          },
                          "clientId": 42,
                          "userId": 99,
                          "id": "business-id"
                        }
                      }
                    ]
                  }
                }
                """;

        String indexed = RedSampleFieldPathIndex.appendIndex(sample);

        assertTrue(indexed.startsWith("### Filter field paths"));
        assertTrue(indexed.contains(sample.trim()));
        assertTrue(indexed.contains("audience.status (string) — e.g. \"PROCESSED\""));
        assertTrue(indexed.contains("audience.segmentType (string) — e.g. \"STANDARD\""));
        assertTrue(indexed.contains("audience.filterGroups.segmentId (string)"));
        assertTrue(indexed.contains("audience.filterGroups.appId (string)"));
        assertTrue(indexed.contains("audience.filterGroups.excluded (boolean)"));
        assertTrue(indexed.contains("additionalInformation.delayReason (string) — e.g. \"NONE\""));
        assertTrue(indexed.contains("audience.channelTypes (array) — e.g. [\"FACEBOOK\"]"));
        assertFalse(indexed.contains("filterGroups[0]"));
        assertFalse(indexed.contains("\n- _id ("));
        assertFalse(indexed.contains("\n- _index ("));
        assertTrue(indexed.contains("Paths matching id / segment"));
        assertTrue(indexed.contains("audience.filterGroups.segmentId (string)"));
    }

    @Test
    void extractsAdEntityPathsWithoutAudienceFields() {
        String sample = """
                {
                  "hits": {
                    "hits": [
                      {
                        "_id": "x",
                        "_index": "ad_entity_190",
                        "_source": {
                          "entityType": "AD_SET",
                          "accountId": 2085979,
                          "createdTime": 1700000000000,
                          "targeting": {
                            "placements": ["FEED"]
                          }
                        }
                      }
                    ]
                  }
                }
                """;

        String indexed = RedSampleFieldPathIndex.appendIndex(sample);

        assertTrue(indexed.startsWith("### Filter field paths"));
        assertTrue(indexed.contains("entityType (string) — e.g. \"AD_SET\""));
        assertTrue(indexed.contains("accountId (number) — e.g. 2085979"));
        assertTrue(indexed.contains("createdTime (number) — e.g. 1700000000000"));
        assertTrue(indexed.contains("targeting.placements (array) — e.g. [\"FEED\"]"));
        assertFalse(indexed.contains("audience."));
        assertFalse(indexed.contains("\n- _id ("));
    }

    @Test
    void returnsUnchangedContentForNonJson() {
        String raw = "not-json-content";
        assertTrue(RedSampleFieldPathIndex.appendIndex(raw).equals(raw));
        assertFalse(RedSampleFieldPathIndex.hasFilterablePaths(raw));
    }

    @Test
    void returnsUnchangedContentForEmptyElasticsearchHits() {
        String raw = "{\"hits\":{\"hits\":[]}}";
        assertEquals(raw, RedSampleFieldPathIndex.appendIndex(raw));
        assertTrue(RedSampleFieldPathIndex.extractPaths(raw).isEmpty());
        assertFalse(RedSampleFieldPathIndex.hasFilterablePaths(raw));
    }

    @Test
    void extractPathsEmptyForElasticsearchHitWithoutSource() {
        String sample = "{\"hits\":{\"hits\":[{\"_id\":\"x\",\"_index\":\"idx\"}]}}";
        assertTrue(RedSampleFieldPathIndex.extractPaths(sample).isEmpty());
        assertFalse(RedSampleFieldPathIndex.hasFilterablePaths(sample));
    }

    @Test
    void extractPathsFromMongoRootArray() {
        String sample = "[{\"entityType\":\"AD_SET\",\"accountId\":1}]";
        var paths = RedSampleFieldPathIndex.extractPaths(sample);
        assertTrue(paths.containsKey("entityType"));
        assertTrue(paths.containsKey("accountId"));
        assertTrue(RedSampleFieldPathIndex.hasFilterablePaths(sample));
    }

    @Test
    void extractPathsEmptyForMongoDocumentsWrapper() {
        assertTrue(RedSampleFieldPathIndex.extractPaths("{\"documents\":[]}").isEmpty());
    }

    @Test
    void indexForLlmIncludesDocumentCount() {
        String sample = SAMPLE_WITH_ONE_HIT();
        RedSampleSchemaIndex indexed = RedSampleFieldPathIndex.indexForLlm(sample, 2, 1);
        assertEquals(1, indexed.documentCount());
        assertFalse(indexed.paths().isEmpty());
        assertTrue(indexed.indexedContent().startsWith("### Filter field paths"));
        assertTrue(indexed.indexedContent().contains(sample));
    }

    @Test
    void indexForLlmExtractsPathsFromSchemaContentButShowsDisplayContent() {
        String schema = """
                {"hits":{"hits":[
                  {"_source":{"entityType":"AD_SET","createdTime":1}},
                  {"_source":{"entityType":"CAMPAIGN","adDeliveryType":"STANDARD"}}
                ]}}
                """;
        String display = """
                {"hits":{"hits":[{"_source":{"entityType":"AD_SET","createdTime":1}}]}}
                """;

        RedSampleSchemaIndex indexed = RedSampleFieldPathIndex.indexForLlm(schema, display, 5, 2);

        assertTrue(indexed.paths().containsKey("createdTime"));
        assertTrue(indexed.paths().containsKey("adDeliveryType"));
        assertTrue(indexed.indexedContent().contains("\"entityType\":\"AD_SET\""));
        assertFalse(indexed.indexedContent().contains("\"entityType\":\"CAMPAIGN\""));
        assertTrue(indexed.indexedContent().contains("Do NOT invent or guess field names"));
    }

    private static String SAMPLE_WITH_ONE_HIT() {
        return "{\"hits\":{\"hits\":[{\"_source\":{\"entityType\":\"AD_SET\"}}]}}";
    }
}
