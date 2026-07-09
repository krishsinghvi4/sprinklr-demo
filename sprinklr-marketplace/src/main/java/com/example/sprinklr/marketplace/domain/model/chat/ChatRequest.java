package com.example.sprinklr.marketplace.domain.model.chat;

public record ChatRequest(
        String userId,
        String conversationId,
        String prompt
) {

    public ChatRequest {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("ChatRequest userId must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("ChatRequest prompt must not be blank");
        }
    }
}
