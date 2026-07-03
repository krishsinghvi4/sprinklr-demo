package com.example.sprinklr.marketplace.domain.port.outbound;

import java.util.Optional;

/**
 * Persists full RED sample-query tool results for reuse within a configurable TTL.
 */
public interface RedSampleQueryCachePort {

    Optional<String> find(String userId, String connectionId, String toolName, String argumentsJson);

    void save(
            String userId,
            String connectionId,
            String toolName,
            String argumentsJson,
            String resultContent
    );
}
