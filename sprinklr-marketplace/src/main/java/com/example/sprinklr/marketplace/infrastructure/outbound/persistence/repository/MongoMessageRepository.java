package com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository;

import com.example.sprinklr.marketplace.domain.model.chat.MessageRole;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.MessageDocument;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface MongoMessageRepository extends ReactiveMongoRepository<MessageDocument, String> {
    // Fetches history backwards (newest first) so we can efficiently limit it
    Flux<MessageDocument> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    Flux<MessageDocument> findByConversationIdAndRoleOrderByCreatedAtDesc(
            String conversationId,
            MessageRole role
    );

    Flux<MessageDocument> findByConversationIdAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
            String conversationId,
            Instant createdAt
    );

    Mono<MessageDocument> findFirstByConversationIdAndRoleOrderByCreatedAtAsc(
            String conversationId,
            MessageRole role
    );

    Mono<Void> deleteByConversationId(String conversationId);
}