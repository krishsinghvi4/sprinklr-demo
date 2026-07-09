package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.ConversationDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationRepository extends ReactiveMongoRepository<ConversationDocument, String> {

    Flux<ConversationDocument> findByUserIdOrderByUpdatedAtDesc(String userId);

    Mono<ConversationDocument> findByIdAndUserId(String id, String userId);

    Mono<Void> deleteByIdAndUserId(String id, String userId);
}
