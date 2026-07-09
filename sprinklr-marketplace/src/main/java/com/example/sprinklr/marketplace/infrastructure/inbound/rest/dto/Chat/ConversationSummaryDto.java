package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ConversationSummaryDto(
        @JsonProperty("id") String id,
        @JsonProperty("preview") String preview,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("updatedAt") Instant updatedAt
) {}
