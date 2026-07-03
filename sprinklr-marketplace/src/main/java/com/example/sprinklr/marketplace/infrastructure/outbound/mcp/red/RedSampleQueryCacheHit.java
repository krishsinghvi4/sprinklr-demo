package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

/**
 * Cached RED sample result paired with its fully-qualified tool label.
 */
public record RedSampleQueryCacheHit(String sampleToolLabel, String resultContent) {
}
