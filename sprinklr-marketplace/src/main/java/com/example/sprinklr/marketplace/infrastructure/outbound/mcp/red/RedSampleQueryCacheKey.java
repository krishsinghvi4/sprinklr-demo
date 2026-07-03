package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

/**
 * Stable cache identity for a RED sample query invocation.
 */
public record RedSampleQueryCacheKey(String id, String scopeArgsJson) {
}
