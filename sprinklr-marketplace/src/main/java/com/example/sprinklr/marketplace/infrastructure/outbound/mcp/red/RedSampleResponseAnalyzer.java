package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

/**
 * Detects whether a RED sample response contains schema-bearing documents.
 */
public final class RedSampleResponseAnalyzer {

    private RedSampleResponseAnalyzer() {
    }

    public static boolean hasSampleDocuments(String content) {
        return RedSampleFieldPathIndex.hasFilterablePaths(content);
    }

    public static int countExtractablePaths(String content) {
        return RedSampleFieldPathIndex.extractPaths(content).size();
    }
}
