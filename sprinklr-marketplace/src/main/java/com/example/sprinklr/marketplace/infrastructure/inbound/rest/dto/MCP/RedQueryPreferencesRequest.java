package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RedQueryPreferencesRequest(
        @NotNull List<@NotBlank String> elasticsearchServerTypes,
        @NotNull List<@Valid MongoServerTypeRequest> mongoServerTypes
) {

    public record MongoServerTypeRequest(
            @NotBlank String serverType,
            @NotNull List<@NotBlank String> collectionNames
    ) {}
}
