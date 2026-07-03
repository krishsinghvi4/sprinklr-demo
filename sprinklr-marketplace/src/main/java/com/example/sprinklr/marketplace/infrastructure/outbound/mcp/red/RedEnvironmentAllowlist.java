package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Closed allowlist of valid RED {@code env} parameter values for Elasticsearch query tools.
 */
@Component
public class RedEnvironmentAllowlist {

    private static final Set<String> ALLOWED = Set.of(
            "prod",
            "prod0",
            "prod2",
            "prod3",
            "prod4",
            "prod5",
            "prod6",
            "prod8",
            "prod11",
            "prod12",
            "prod15",
            "prod16",
            "prod17",
            "prod18",
            "prod19",
            "prod21",
            "spr-uat",
            "azrqa",
            "qa6"
    );

    public boolean isAllowed(String env) {
        if (env == null || env.isBlank()) {
            return false;
        }
        return ALLOWED.contains(env.trim());
    }

    public String allowedValuesDescription() {
        return ALLOWED.stream().sorted().collect(Collectors.joining(", "));
    }

    public Set<String> allowedValues() {
        return new LinkedHashSet<>(ALLOWED);
    }
}
