package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ConnectMcpServerRequest(
        @NotBlank String catalogServerId,
        /** May be empty for LOCAL_ONLY servers; required fields validated per catalog entry. */
        @NotNull Map<String, String> credentials
) {}
