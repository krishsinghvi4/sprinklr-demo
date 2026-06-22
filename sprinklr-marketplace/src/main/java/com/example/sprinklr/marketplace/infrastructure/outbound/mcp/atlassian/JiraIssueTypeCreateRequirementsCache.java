package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches required create fields from the latest {@code getJiraIssueTypeMetaWithFields}
 * call per connection + project + issue type so preflight can validate completeness.
 */
@Component
public class JiraIssueTypeCreateRequirementsCache {

    private final Map<String, List<JiraIssueTypeMetadataProcessor.RequiredField>> requirementsByKey =
            new ConcurrentHashMap<>();

    public void put(
            String connectionId,
            String projectKey,
            String issueTypeKey,
            List<JiraIssueTypeMetadataProcessor.RequiredField> requiredFields
    ) {
        if (connectionId == null || connectionId.isBlank()
                || projectKey == null || projectKey.isBlank()
                || issueTypeKey == null || issueTypeKey.isBlank()
                || requiredFields == null || requiredFields.isEmpty()) {
            return;
        }
        requirementsByKey.put(cacheKey(connectionId, projectKey, issueTypeKey), List.copyOf(requiredFields));
    }

    public void putAliases(
            String connectionId,
            String projectKey,
            List<String> issueTypeKeys,
            List<JiraIssueTypeMetadataProcessor.RequiredField> requiredFields
    ) {
        if (issueTypeKeys == null || issueTypeKeys.isEmpty()) {
            return;
        }
        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        for (String key : issueTypeKeys) {
            if (key != null && !key.isBlank()) {
                uniqueKeys.add(key);
            }
        }
        for (String key : uniqueKeys) {
            put(connectionId, projectKey, key, requiredFields);
        }
    }

    public Optional<List<JiraIssueTypeMetadataProcessor.RequiredField>> get(
            String connectionId,
            String projectKey,
            String issueTypeKey
    ) {
        if (connectionId == null || projectKey == null || issueTypeKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(requirementsByKey.get(cacheKey(connectionId, projectKey, issueTypeKey)));
    }

    public Optional<List<JiraIssueTypeMetadataProcessor.RequiredField>> resolve(
            String connectionId,
            String projectKey,
            List<String> issueTypeLookupKeys
    ) {
        if (issueTypeLookupKeys != null) {
            for (String key : issueTypeLookupKeys) {
                Optional<List<JiraIssueTypeMetadataProcessor.RequiredField>> found =
                        get(connectionId, projectKey, key);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return getWhenSingleForProject(connectionId, projectKey);
    }

    private Optional<List<JiraIssueTypeMetadataProcessor.RequiredField>> getWhenSingleForProject(
            String connectionId,
            String projectKey
    ) {
        String prefix = connectionId + "|" + normalize(projectKey) + "|";
        List<List<JiraIssueTypeMetadataProcessor.RequiredField>> matches = new ArrayList<>();
        for (Map.Entry<String, List<JiraIssueTypeMetadataProcessor.RequiredField>> entry
                : requirementsByKey.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                matches.add(entry.getValue());
            }
        }
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        List<JiraIssueTypeMetadataProcessor.RequiredField> first = matches.get(0);
        boolean allSame = matches.stream().allMatch(first::equals);
        if (allSame) {
            return Optional.of(first);
        }
        return Optional.empty();
    }

    static String cacheKey(String connectionId, String projectKey, String issueTypeKey) {
        return connectionId + "|" + normalize(projectKey) + "|" + normalize(issueTypeKey);
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
