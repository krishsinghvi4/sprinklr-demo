package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ConversationListResponse(
        @JsonProperty("conversations") List<ConversationSummaryDto> conversations
) {}
