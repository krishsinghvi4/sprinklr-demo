package com.example.sprinklr.marketplace.infrastructure.config.security;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class ApiAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final byte[] UNAUTHORIZED_BODY =
            "{\"error\":\"Unauthorized\",\"message\":\"Valid Bearer JWT required\"}"
                    .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(UNAUTHORIZED_BODY);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}

@Component
class OAuth2FailureHandler implements ServerAuthenticationFailureHandler {

    private static final String FRONTEND_CALLBACK_URL = "http://localhost:5173/auth/callback";

    @Override
    public Mono<Void> onAuthenticationFailure(
            org.springframework.security.web.server.WebFilterExchange webFilterExchange,
            AuthenticationException exception
    ) {
        System.err.println("[OAuth2] Authentication failed: " + exception.getMessage());
        if (exception.getCause() != null) {
            System.err.println("[OAuth2] Cause: " + exception.getCause().getMessage());
        }

        String encodedError = URLEncoder.encode(
                exception.getMessage() != null ? exception.getMessage() : "OAuth2 login failed",
                StandardCharsets.UTF_8
        );

        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        webFilterExchange.getExchange().getResponse().getHeaders()
                .setLocation(java.net.URI.create(FRONTEND_CALLBACK_URL + "?error=" + encodedError));
        return webFilterExchange.getExchange().getResponse().setComplete();
    }
}
