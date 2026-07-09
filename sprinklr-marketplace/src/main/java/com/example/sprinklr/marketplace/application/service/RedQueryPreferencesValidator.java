package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.RedQueryPreferencesRequest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RedQueryPreferencesValidator {

    private RedQueryPreferencesValidator() {
    }

    public static RedQueryPreferences toDomain(RedQueryPreferencesRequest request) {
        validate(request);
        List<RedQueryPreferences.MongoServerTypeConfig> mongoConfigs = request.mongoServerTypes().stream()
                .map(entry -> new RedQueryPreferences.MongoServerTypeConfig(
                        entry.serverType().trim(),
                        entry.collectionNames().stream().map(String::trim).toList()))
                .toList();
        List<String> esTypes = request.elasticsearchServerTypes().stream()
                .map(String::trim)
                .toList();
        return new RedQueryPreferences(esTypes, mongoConfigs);
    }

    private static void validate(RedQueryPreferencesRequest request) {
        for (String serverType : request.elasticsearchServerTypes()) {
            if (serverType == null || serverType.isBlank()) {
                throw new IllegalArgumentException("Elasticsearch serverType values must not be blank");
            }
        }

        Set<String> mongoServerTypes = new HashSet<>();
        for (RedQueryPreferencesRequest.MongoServerTypeRequest entry : request.mongoServerTypes()) {
            if (entry.serverType() == null || entry.serverType().isBlank()) {
                throw new IllegalArgumentException("Mongo serverType values must not be blank");
            }
            String normalized = entry.serverType().trim();
            if (!mongoServerTypes.add(normalized)) {
                throw new IllegalArgumentException("Duplicate Mongo serverType: " + normalized);
            }
            if (entry.collectionNames() == null || entry.collectionNames().isEmpty()) {
                throw new IllegalArgumentException(
                        "Mongo serverType '" + normalized + "' must have at least one collectionName");
            }
            for (String collectionName : entry.collectionNames()) {
                if (collectionName == null || collectionName.isBlank()) {
                    throw new IllegalArgumentException(
                            "Collection names for Mongo serverType '" + normalized + "' must not be blank");
                }
            }
        }
    }
}
