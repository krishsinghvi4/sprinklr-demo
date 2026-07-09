package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;

import java.util.List;

public record RedQueryPreferencesResponse(
        List<String> elasticsearchServerTypes,
        List<MongoServerTypeDto> mongoServerTypes
) {

    public static RedQueryPreferencesResponse from(RedQueryPreferences preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return empty();
        }
        return new RedQueryPreferencesResponse(
                preferences.elasticsearchServerTypes(),
                preferences.mongoServerTypes().stream()
                        .map(entry -> new MongoServerTypeDto(entry.serverType(), entry.collectionNames()))
                        .toList()
        );
    }

    public static RedQueryPreferencesResponse empty() {
        return new RedQueryPreferencesResponse(List.of(), List.of());
    }

    public record MongoServerTypeDto(String serverType, List<String> collectionNames) {}
}
