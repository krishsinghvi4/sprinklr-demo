package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.infrastructure.config.security.JwtUtil;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.TokenValidationRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.TokenValidationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URI;

@RestController
public class AuthController {

    private static final String MICROSOFT_OAUTH2_AUTHORIZATION_URL = "/oauth2/authorization/microsoft";

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/auth/microsoft/login")
    public Mono<Void> microsoftLogin(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(MICROSOFT_OAUTH2_AUTHORIZATION_URL));
        return response.setComplete();
    }

    /**
     * OAuth2 callback is handled by Spring Security at /login/oauth2/code/microsoft.
     * The frontend receives the JWT at http://localhost:5173/auth/callback?token=...
     */
    @PostMapping("/api/auth/validate")
    public Mono<TokenValidationResponse> validateToken(@RequestBody TokenValidationRequest request) {
        return Mono.fromCallable(() -> {
                    if (request.token() == null || request.token().isBlank()) {
                        return new TokenValidationResponse(false, null, null);
                    }

                    if (!jwtUtil.validateToken(request.token())) {
                        return new TokenValidationResponse(false, null, null);
                    }

                    return new TokenValidationResponse(
                            true,
                            jwtUtil.extractUserId(request.token()),
                            jwtUtil.extractEmail(request.token())
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
