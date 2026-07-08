package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import java.util.Map;

/**
 * Sample JSON plus extracted filter field paths prepared for the LLM.
 */
public record RedSampleSchemaIndex(
        String rawContent,
        Map<String, RedSampleFilterPath> paths,
        String indexedContent,
        int documentCount
) {
}
