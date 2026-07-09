package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Appends runtime current-date context so the LLM can compute relative date filters correctly.
 */
public final class LlmCurrentDateContext {

    private LlmCurrentDateContext() {
    }

    public static String append(String systemPrompt) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return systemPrompt;
        }
        Instant now = Instant.now();
        ZonedDateTime utc = now.atZone(ZoneOffset.UTC);
        long epochMs = now.toEpochMilli();
        long thirtyDaysAgoMs = now.minus(30, ChronoUnit.DAYS).toEpochMilli();

        return systemPrompt.trim()
                + "\n\n## Current date context (UTC)\n"
                + "Today's date: " + utc.toLocalDate() + "\n"
                + "Current time: " + utc.toLocalTime().withNano(0) + " UTC\n"
                + "Current epoch milliseconds: " + epochMs + "\n"
                + "30 days ago epoch milliseconds: " + thirtyDaysAgoMs + "\n"
                + "For relative date filters (e.g. \"last month\", \"past 30 days\", \"last week\"), "
                + "compute Mongo/Elasticsearch $gte/$lte cutoffs from today's date above. "
                + "Do not guess years or reuse stale example dates from prior turns.\n"
                + "When formatting epoch milliseconds for the user (tables, prose, summaries), "
                + "convert each value to its true calendar date from the epoch — never assume the year is 2024. "
                + "Ignore createdTime on sample/schema-probe rows when answering; only use execute tool results.";
    }
}
