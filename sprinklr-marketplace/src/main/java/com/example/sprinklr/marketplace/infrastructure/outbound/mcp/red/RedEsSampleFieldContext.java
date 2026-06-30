package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Holds sample field paths for the current chat turn thread so execute-query normalization
 * can use schema discovered earlier in the same turn.
 */
@Component
public class RedEsSampleFieldContext {

    private final ThreadLocal<RedEsSampleFieldCatalog> catalog = new ThreadLocal<>();

    public void set(RedEsSampleFieldCatalog sampleCatalog) {
        if (sampleCatalog == null || sampleCatalog.isEmpty()) {
            catalog.remove();
            return;
        }
        catalog.set(sampleCatalog);
    }

    public Optional<RedEsSampleFieldCatalog> current() {
        return Optional.ofNullable(catalog.get());
    }

    public void clear() {
        catalog.remove();
    }
}
