package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface MongoMessageRepository extends ReactiveMongoRepository<MessageDocument, String> {
    // Fetches history backwards (newest first) so we can efficiently limit it
    Flux<MessageDocument> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}