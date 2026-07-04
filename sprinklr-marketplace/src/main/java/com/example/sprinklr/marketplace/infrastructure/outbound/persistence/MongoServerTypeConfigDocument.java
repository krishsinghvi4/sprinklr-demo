package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import java.util.List;

public record MongoServerTypeConfigDocument(
        String serverType,
        List<String> collectionNames
) {}
