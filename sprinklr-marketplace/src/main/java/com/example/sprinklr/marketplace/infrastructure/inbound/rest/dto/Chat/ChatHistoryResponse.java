package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat;

import com.example.sprinklr.marketplace.domain.model.chat.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatHistoryResponse(
        @JsonProperty("messages") List<MessageDto> messages,
        @JsonProperty("hasMore") boolean hasMore
) {
    public static ChatHistoryResponse fromMessages(List<Message> messages, boolean hasMore) {
        return new ChatHistoryResponse(
                messages.stream()
                        .map(MessageDto::fromDomain)
                        .toList(),
                hasMore
        );
    }
}
