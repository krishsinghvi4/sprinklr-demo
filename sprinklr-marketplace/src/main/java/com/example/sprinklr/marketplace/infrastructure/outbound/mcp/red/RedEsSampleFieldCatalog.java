package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Field paths discovered from a RED Elasticsearch sample response in the current turn.
 */
public record RedEsSampleFieldCatalog(Set<String> paths, Set<String> arrayPaths) {

    public RedEsSampleFieldCatalog {
        paths = paths == null ? Set.of() : Set.copyOf(paths);
        arrayPaths = arrayPaths == null ? Set.of() : Set.copyOf(arrayPaths);
    }

    public static RedEsSampleFieldCatalog empty() {
        return new RedEsSampleFieldCatalog(Set.of(), Set.of());
    }

    public boolean isEmpty() {
        return paths.isEmpty();
    }

    /**
     * Maps an LLM-guessed filter field to an exact path from sample data, if unambiguous.
     */
    public Optional<String> resolve(String queryField) {
        if (queryField == null || queryField.isBlank() || paths.isEmpty()) {
            return Optional.empty();
        }
        if (paths.contains(queryField)) {
            return Optional.of(queryField);
        }
        if (queryField.endsWith(".keyword")) {
            String withoutKeyword = queryField.substring(0, queryField.length() - ".keyword".length());
            Optional<String> resolved = resolveExactOrSuffix(withoutKeyword);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return resolveExactOrSuffix(queryField);
    }

    public boolean isArrayField(String path) {
        return arrayPaths.contains(path);
    }

    public List<String> displayPaths() {
        return paths.stream()
                .sorted()
                .map(path -> isArrayField(path)
                        ? path + " (array — use terms query with a JSON array value)"
                        : path)
                .toList();
    }

    private Optional<String> resolveExactOrSuffix(String candidate) {
        if (paths.contains(candidate)) {
            return Optional.of(candidate);
        }
        Set<String> suffixMatches = new LinkedHashSet<>();
        for (String path : paths) {
            if (path.equals(candidate) || path.endsWith("." + candidate)) {
                suffixMatches.add(path);
            }
        }
        return suffixMatches.size() == 1
                ? Optional.of(suffixMatches.iterator().next())
                : Optional.empty();
    }
}
