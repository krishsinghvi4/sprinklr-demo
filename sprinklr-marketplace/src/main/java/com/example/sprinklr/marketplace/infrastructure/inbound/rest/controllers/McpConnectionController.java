package com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.application.service.RedQueryPreferencesValidator;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.ConnectMcpServerRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.McpConnectionResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.RedQueryPreferencesRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.RedQueryPreferencesResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.TeamsWebhookResponse;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

    @GetMapping("/{connectionId}/teams-webhook")
    public TeamsWebhookResponse getTeamsWebhook(@PathVariable String connectionId) {
        String userId = authenticatedUserResolver.requireUserId();
        String webhookUrl = marketplaceService.getTeamsWebhookUrl(userId, connectionId);
        if (webhookUrl == null || webhookUrl.isBlank()) {
            throw new IllegalArgumentException("Webhook URL not found — disconnect and reconnect Teams Messages");
        }
        return new TeamsWebhookResponse(webhookUrl);
    }

    @GetMapping("/{connectionId}/red-query-preferences")
    public RedQueryPreferencesResponse getRedQueryPreferences(@PathVariable String connectionId) {
        String userId = authenticatedUserResolver.requireUserId();
        return RedQueryPreferencesResponse.from(marketplaceService.getRedQueryPreferences(userId, connectionId));
    }

    @PutMapping(
            path = "/{connectionId}/red-query-preferences",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public RedQueryPreferencesResponse updateRedQueryPreferences(
            @PathVariable String connectionId,
            @Valid @RequestBody RedQueryPreferencesRequest request
    ) {
        String userId = authenticatedUserResolver.requireUserId();
        return RedQueryPreferencesResponse.from(
                marketplaceService.updateRedQueryPreferences(
                        userId,
                        connectionId,
                        RedQueryPreferencesValidator.toDomain(request)));
    }
}
