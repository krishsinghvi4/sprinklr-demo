package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record UserMcpConfigListResponseDto(
        @JsonProperty("configs") List<UserMcpConfigResponseDto> configs
) {}
