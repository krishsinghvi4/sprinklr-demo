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

        assertTrue(indexed.contains("audience.status (string)"));
        assertTrue(indexed.contains("audience.segmentType (string)"));
        assertTrue(indexed.contains("audience.filterGroups.segmentId (string)"));
        assertTrue(indexed.contains("audience.filterGroups.appId (string)"));
        assertTrue(indexed.contains("audience.filterGroups.excluded (boolean)"));
        assertTrue(indexed.contains("additionalInformation.delayReason (string)"));
        assertTrue(indexed.contains("audience.channelTypes (array)"));
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

        assertTrue(indexed.contains("entityType (string)"));
        assertTrue(indexed.contains("accountId (number)"));
        assertTrue(indexed.contains("createdTime (number)"));
        assertTrue(indexed.contains("targeting.placements (array)"));
        assertFalse(indexed.contains("audience."));
        assertFalse(indexed.contains("\n- _id ("));
    }

    @Test
    void returnsUnchangedContentForNonJson() {
        String raw = "not-json-content";
        assertTrue(RedSampleFieldPathIndex.appendIndex(raw).equals(raw));
    }

    @Test
    void returnsUnchangedContentForEmptyElasticsearchHits() {
        String raw = "{\"hits\":{\"hits\":[]}}";
        assertEquals(raw, RedSampleFieldPathIndex.appendIndex(raw));
    }
}
