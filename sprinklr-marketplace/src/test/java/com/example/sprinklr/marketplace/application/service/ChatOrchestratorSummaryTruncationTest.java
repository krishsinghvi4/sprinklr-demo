package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatOrchestratorSummaryTruncationTest {

    @Test
    void truncatesPendingWorkflowSummaryAtConfiguredLimit() {
        McpProperties properties = new McpProperties();
        properties.getToolSelection().setContinuationSummaryMaxChars(5000);

        String truncated = truncateForSummary(properties, "a".repeat(6000));

        assertEquals(5000 + "…(truncated)".length(), truncated.length());
        assertTrue(truncated.endsWith("…(truncated)"));
    }

    @Test
    void leavesShortSummariesUntouched() {
        McpProperties properties = new McpProperties();
        properties.getToolSelection().setContinuationSummaryMaxChars(5000);

        String truncated = truncateForSummary(properties, "short summary");

        assertEquals("short summary", truncated);
    }

    private static String truncateForSummary(McpProperties properties, String content) {
        String collapsed = content.strip();
        int max = Math.max(1, properties.getToolSelection().getContinuationSummaryMaxChars());
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "…(truncated)";
    }
}
