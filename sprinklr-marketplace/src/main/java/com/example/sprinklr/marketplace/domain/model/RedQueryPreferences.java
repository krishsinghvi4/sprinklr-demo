package com.example.sprinklr.marketplace.domain.model;

import java.util.List;

/**
 * User-configured allowlists for RED Elasticsearch and Mongo query tools on a single connection.
 */
public record RedQueryPreferences(
        List<String> elasticsearchServerTypes,
        List<MongoServerTypeConfig> mongoServerTypes
) {

    public RedQueryPreferences {
        elasticsearchServerTypes = elasticsearchServerTypes == null ? List.of() : List.copyOf(elasticsearchServerTypes);
        mongoServerTypes = mongoServerTypes == null ? List.of() : List.copyOf(mongoServerTypes);
    }

    public record MongoServerTypeConfig(String serverType, List<String> collectionNames) {

        public MongoServerTypeConfig {
            if (serverType == null || serverType.isBlank()) {
                throw new IllegalArgumentException("Mongo serverType must not be blank");
            }
            collectionNames = collectionNames == null ? List.of() : List.copyOf(collectionNames);
        }
    }

    public boolean isEmpty() {
        return elasticsearchServerTypes.isEmpty() && mongoServerTypes.isEmpty();
    }

    public boolean hasConfiguredValues() {
        return !isEmpty();
    }
}
