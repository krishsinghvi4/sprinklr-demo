package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record ConnectMcpServerRequest(
        @NotBlank String catalogServerId,
        @NotEmpty Map<String, String> credentials
) {}
