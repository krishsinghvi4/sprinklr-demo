package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedEsSampleFieldExtractorTest {

    @Test
    void parsesCatalogFromElasticsearchHits() {
        String sample = """
                {
                  "hits": {
                    "hits": [
                      {
                        "_source": {
                          "audience": {
                            "channelTypes": ["FACEBOOK", "SNAPCHAT"]
                          },
                          "createdAt": 1710000000000
                        }
                      }
                    ]
                  }
                }
                """;

        RedEsSampleFieldCatalog catalog = RedEsSampleFieldExtractor.parseCatalog(sample);

        assertTrue(catalog.paths().contains("audience.channelTypes"));
        assertTrue(catalog.arrayPaths().contains("audience.channelTypes"));
        assertTrue(catalog.paths().contains("createdAt"));
        assertFalse(catalog.paths().stream().anyMatch(path -> path.contains("channelTypes.keyword")));
    }
}
