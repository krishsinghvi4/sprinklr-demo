package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.Chat;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConversationListResponse(
        @JsonProperty("conversations") List<ConversationSummaryDto> conversations,
        @JsonProperty("page") int page,
        @JsonProperty("pageSize") int pageSize,
        @JsonProperty("hasMore") boolean hasMore,
        @JsonProperty("totalCount") long totalCount
) {}
