package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedEsSampleFieldCatalogTest {

    private final RedEsSampleFieldCatalog catalog = new RedEsSampleFieldCatalog(
            Set.of("audience.channelTypes", "createdAt", "assetId"),
            Set.of("audience.channelTypes"));

    @Test
    void resolvesExactPath() {
        assertEquals("assetId", catalog.resolve("assetId").orElseThrow());
    }

    @Test
    void resolvesSuffixWhenUnique() {
        assertEquals("audience.channelTypes", catalog.resolve("channelTypes").orElseThrow());
    }

    @Test
    void stripsKeywordSuffixBeforeMatching() {
        assertEquals("audience.channelTypes", catalog.resolve("channelTypes.keyword").orElseThrow());
    }

    @Test
    void emptyWhenSuffixMatchesMultiplePaths() {
        RedEsSampleFieldCatalog ambiguous = new RedEsSampleFieldCatalog(
                Set.of("foo.channelTypes", "bar.channelTypes"),
                Set.of("foo.channelTypes", "bar.channelTypes"));

        assertTrue(ambiguous.resolve("channelTypes").isEmpty());
    }
}
