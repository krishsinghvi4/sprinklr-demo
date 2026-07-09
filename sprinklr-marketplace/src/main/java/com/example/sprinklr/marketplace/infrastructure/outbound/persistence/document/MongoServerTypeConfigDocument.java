package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document;

import java.util.List;

public record MongoServerTypeConfigDocument(
        String serverType,
        List<String> collectionNames
) {}
