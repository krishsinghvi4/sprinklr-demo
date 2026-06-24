package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

public record LlmUsageContext(
        String userId,
        String conversationId
) {
}
