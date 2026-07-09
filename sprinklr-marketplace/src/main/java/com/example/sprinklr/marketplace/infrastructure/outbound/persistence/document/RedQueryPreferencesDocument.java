package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import java.util.List;

public record RedQueryPreferencesDocument(
        List<String> elasticsearchServerTypes,
        List<MongoServerTypeConfigDocument> mongoServerTypes
) {}
