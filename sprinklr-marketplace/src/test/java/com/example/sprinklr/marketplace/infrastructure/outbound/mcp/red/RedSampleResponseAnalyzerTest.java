package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedSampleResponseAnalyzerTest {

    @Test
    void detectsEmptyElasticsearchHits() {
        assertFalse(RedSampleResponseAnalyzer.hasSampleDocuments("{\"hits\":{\"hits\":[]}}"));
        assertEquals(0, RedSampleResponseAnalyzer.countExtractablePaths("{\"hits\":{\"hits\":[]}}"));
    }

    @Test
    void detectsEmptyMongoDocumentsArray() {
        assertFalse(RedSampleResponseAnalyzer.hasSampleDocuments("{\"documents\":[]}"));
        assertFalse(RedSampleResponseAnalyzer.hasSampleDocuments("[]"));
    }

    @Test
    void rejectsElasticsearchHitWithoutSource() {
        assertFalse(RedSampleResponseAnalyzer.hasSampleDocuments("{\"hits\":{\"hits\":[{\"_id\":\"x\"}]}}"));
        assertEquals(0, RedSampleResponseAnalyzer.countExtractablePaths("{\"hits\":{\"hits\":[{\"_id\":\"x\"}]}}"));
    }

    @Test
    void detectsNonEmptyElasticsearchHits() {
        String sample = """
                {"hits":{"hits":[{"_source":{"adDeliveryType":"STANDARD"}}]}}
                """;
        assertTrue(RedSampleResponseAnalyzer.hasSampleDocuments(sample));
        assertTrue(RedSampleResponseAnalyzer.countExtractablePaths(sample) > 0);
    }
}
