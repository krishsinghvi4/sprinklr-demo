package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ConnectMcpServerRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.McpConnectionResponse;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp/connections")
public class McpConnectionController {

    private final McpMarketplaceService marketplaceService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public McpConnectionController(
            McpMarketplaceService marketplaceService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.marketplaceService = marketplaceService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public McpConnectionResponse connect(@Valid @RequestBody ConnectMcpServerRequest request) {
        String userId = authenticatedUserResolver.requireUserId();
        McpMarketplaceService.ConnectionView connection = marketplaceService.connect(
                userId,
                request.catalogServerId(),
                request.credentials()
        );
        return McpConnectionResponse.from(connection);
    }

    @DeleteMapping("/{connectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable String connectionId) {
        String userId = authenticatedUserResolver.requireUserId();
        marketplaceService.disconnect(userId, connectionId);
    }
}
