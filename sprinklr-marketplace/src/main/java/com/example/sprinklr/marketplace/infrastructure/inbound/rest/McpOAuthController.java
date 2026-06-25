package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.application.service.McpOAuthService;
import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpConnectionException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpDiscoveryException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpOAuthException;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/mcp/oauth")
public class McpOAuthController {

    private static final Logger log = LoggerFactory.getLogger(McpOAuthController.class);

    private final McpOAuthService oauthService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final McpProperties mcpProperties;

    public McpOAuthController(
            McpOAuthService oauthService,
            AuthenticatedUserResolver authenticatedUserResolver,
            McpProperties mcpProperties
    ) {
        this.oauthService = oauthService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.mcpProperties = mcpProperties;
    }

    @GetMapping("/start/{catalogServerId}")
    public OAuthStartResponse start(@PathVariable String catalogServerId) {
        String userId = authenticatedUserResolver.requireUserId();
        String authUrl = oauthService.startOAuth(userId, catalogServerId);
        return new OAuthStartResponse(authUrl);
    }

    public record OAuthStartResponse(String authorizationUrl) {}

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        log.info("[MCP] OAuth callback received codePresent={} statePresent={} error={}",
                code != null && !code.isBlank(),
                state != null && !state.isBlank(),
                error);

        if (error != null && !error.isBlank()) {
            log.warn("[MCP] OAuth callback provider error={}", error);
            return redirectToError("OAuth authorization was denied. Please try again.");
        }

        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            log.warn("[MCP] OAuth callback missing code/state");
            return redirectToError("OAuth callback was incomplete. Please try again.");
        }

        try {
            oauthService.handleCallback(state, code);
            return redirectTo(mcpProperties.getOauthSuccessRedirectUrl());
        } catch (McpOAuthException exception) {
            log.warn("[MCP] OAuth callback failed: {}", exception.getMessage());
            return redirectToError(exception.getUserMessage());
        } catch (McpConnectionException exception) {
            log.warn("[MCP] OAuth callback MCP connection error: {}", exception.getMessage());
            return redirectToError(exception.getUserMessage());
        } catch (McpDiscoveryException exception) {
            log.warn("[MCP] OAuth callback MCP discovery error: {}", exception.getMessage());
            return redirectToError(exception.getUserMessage());
        } catch (Exception exception) {
            log.error("[MCP] OAuth callback unexpected error", exception);
            return redirectToError("OAuth connection failed. Please try again.");
        }
    }

    private ResponseEntity<Void> redirectToError(String message) {
        URI location = UriComponentsBuilder.fromUriString(mcpProperties.getOauthErrorRedirectUrl())
                .queryParam("message", message)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }

    private ResponseEntity<Void> redirectTo(String location) {
        URI uri = UriComponentsBuilder.fromUriString(location)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
    }
}
