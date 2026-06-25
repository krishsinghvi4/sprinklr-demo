package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import java.util.List;

/**
 * One directed edge in a persisted tool dependency graph: {@code tool} depends on {@code prerequisites}
 * running first. Stored as a list entry so fully-qualified tool names (with dots) are not used as
 * MongoDB map keys.
 */
public record EdgeDocument(
        String tool,
        List<String> prerequisites
) {}
