package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCurrentDateContextTest {

    @Test
    void appendsCurrentDateSection() {
        String enriched = LlmCurrentDateContext.append("base prompt");

        assertTrue(enriched.contains("## Current date context (UTC)"));
        assertTrue(enriched.contains("Today's date:"));
        assertTrue(enriched.contains("30 days ago epoch milliseconds:"));
        assertTrue(enriched.contains("never assume the year is 2024"));
        assertTrue(enriched.contains("only use execute tool results"));
        assertTrue(enriched.startsWith("base prompt"));
    }
}
