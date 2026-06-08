package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ChatHistoryResponse(
        @JsonProperty("messages") List<MessageDto> messages
) {
    public static ChatHistoryResponse fromMessages(List<Message> messages) {
        return new ChatHistoryResponse(
                messages.stream()
                        .map(MessageDto::fromDomain)
                        .toList()
        );
    }
}
