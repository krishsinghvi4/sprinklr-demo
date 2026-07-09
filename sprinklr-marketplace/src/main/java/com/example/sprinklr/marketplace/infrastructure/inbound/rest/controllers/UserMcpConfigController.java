package com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers;

import com.example.sprinklr.marketplace.application.service.UserMcpConfigService;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.CreateUserMcpConfigRequestDto;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.UserMcpConfigListResponseDto;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.MCP.UserMcpConfigResponseDto;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mcp/user-configs")
public class UserMcpConfigController {

    private final UserMcpConfigService userMcpConfigService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public UserMcpConfigController(
            UserMcpConfigService userMcpConfigService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.userMcpConfigService = userMcpConfigService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UserMcpConfigResponseDto createConfig(@RequestBody CreateUserMcpConfigRequestDto request) {
        String userId = authenticatedUserResolver.requireUserId();
        return UserMcpConfigResponseDto.from(
                userMcpConfigService.createConfig(userId, request.toDomain()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public UserMcpConfigListResponseDto listConfigs() {
        String userId = authenticatedUserResolver.requireUserId();
        return new UserMcpConfigListResponseDto(
                userMcpConfigService.listConfigs(userId).stream()
                        .map(UserMcpConfigResponseDto::from)
                        .toList()
        );
    }

    @DeleteMapping("/{configId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConfig(@PathVariable String configId) {
        String userId = authenticatedUserResolver.requireUserId();
        userMcpConfigService.deleteConfig(userId, configId);
    }
}
