package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat;

import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record MessageDto(
        @JsonProperty("id") String id,
        @JsonProperty("conversationId") String conversationId,
        @JsonProperty("role") String role,
        @JsonProperty("content") String content,
        @JsonProperty("createdAt") Instant createdAt
) {
    public static MessageDto fromDomain(Message message) {
        return new MessageDto(
                message.id(),
                message.conversationId(),
                message.role().name().toLowerCase(),
                message.content(),
                message.createdAt()
        );
    }
}
