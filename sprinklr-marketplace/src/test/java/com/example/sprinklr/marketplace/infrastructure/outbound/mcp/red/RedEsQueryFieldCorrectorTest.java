package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedEsQueryFieldCorrectorTest {

    private final RedEsSampleFieldCatalog catalog = new RedEsSampleFieldCatalog(
            Set.of("audience.channelTypes", "createdAt"),
            Set.of("audience.channelTypes"));

    @Test
    void rewritesKeywordTermToTermsOnSamplePath() {
        String query = """
                {"query":{"bool":{"must":[{"term":{"channelTypes.keyword":"FACEBOOK"}}]}},"sort":[{"createdAt":{"order":"desc"}}],"size":5}
                """;

        String fixed = RedEsQueryFieldCorrector.correctQueryFields(query, catalog);

        assertTrue(fixed.contains("\"audience.channelTypes\":[\"FACEBOOK\"]"));
        assertFalse(fixed.contains("channelTypes.keyword"));
    }

    @Test
    void rewritesAuditLogAssetIdWithoutKeywordSuffix() {
        RedEsSampleFieldCatalog auditCatalog = new RedEsSampleFieldCatalog(
                Set.of("assetId"),
                Set.of());

        String query = """
                {"query":{"bool":{"must":[{"term":{"assetId.keyword":"abc123"}}]}}}
                """;

        String fixed = RedEsQueryFieldCorrector.correctQueryFields(query, auditCatalog);

        assertTrue(fixed.contains("\"assetId\":[\"abc123\"]"));
        assertFalse(fixed.contains("assetId.keyword"));
    }

    @Test
    void leavesQueryUnchangedWithoutSampleCatalog() {
        String query = "{\"query\":{\"term\":{\"channelTypes.keyword\":\"FACEBOOK\"}}}";

        assertEquals(query, RedEsQueryFieldCorrector.correctQueryFields(query, RedEsSampleFieldCatalog.empty()));
    }

    private static void assertEquals(String expected, String actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }
}
