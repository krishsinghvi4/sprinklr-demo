package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface MongoConversationRepository extends ReactiveMongoRepository<ConversationDocument, String> {
}