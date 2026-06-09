package com.example.sprinklr.marketplace.infrastructure.config.security;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.User;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Component
public class OAuth2SuccessHandler implements ServerAuthenticationSuccessHandler {

    private static final String FRONTEND_CALLBACK_URL = "http://localhost:5173/auth/callback";
    private static final String OAUTH2_PASSWORD_PLACEHOLDER = "OAUTH2_AUTHENTICATED";

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    public OAuth2SuccessHandler(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();

        return Mono.fromCallable(() -> resolveUserAndToken(oauth2User))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(token -> redirectWithToken(webFilterExchange, token))
                .onErrorResume(IllegalArgumentException.class, error -> {
                    System.err.println("[OAuth2] Post-login validation failed: " + error.getMessage());
                    return redirectWithError(webFilterExchange, error.getMessage());
                });
    }

    private String resolveUserAndToken(OAuth2User oauth2User) {
        String email = extractEmail(oauth2User.getAttributes());
        if (!MicrosoftEmailValidator.isValid(email)) {
            throw new IllegalArgumentException("Email is not a valid Outlook/Microsoft address: " + email);
        }

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.save(
                        new User(null, email, OAUTH2_PASSWORD_PLACEHOLDER, LocalDateTime.now())
                ));

        return jwtUtil.generateToken(user.id(), user.email());
    }

    private Mono<Void> redirectWithToken(WebFilterExchange webFilterExchange, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        webFilterExchange.getExchange().getResponse().getHeaders()
                .setLocation(java.net.URI.create(FRONTEND_CALLBACK_URL + "?token=" + encodedToken));
        return webFilterExchange.getExchange().getResponse().setComplete();
    }

    private Mono<Void> redirectWithError(WebFilterExchange webFilterExchange, String message) {
        String encodedError = URLEncoder.encode(message, StandardCharsets.UTF_8);
        webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.FOUND);
        webFilterExchange.getExchange().getResponse().getHeaders()
                .setLocation(java.net.URI.create(FRONTEND_CALLBACK_URL + "?error=" + encodedError));
        return webFilterExchange.getExchange().getResponse().setComplete();
    }

    private String extractEmail(Map<String, Object> attributes) {
        Object email = attributes.get("email");
        if (email instanceof String emailValue && !emailValue.isBlank()) {
            return emailValue;
        }

        Object preferredUsername = attributes.get("preferred_username");
        if (preferredUsername instanceof String username && !username.isBlank()) {
            return username;
        }

        Object upn = attributes.get("upn");
        if (upn instanceof String upnValue && !upnValue.isBlank()) {
            return upnValue;
        }

        throw new IllegalArgumentException("Microsoft OAuth2 response did not include an email address");
    }
}
