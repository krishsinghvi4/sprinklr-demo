package com.example.sprinklr.marketplace.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Builds the dedicated {@link WebClient} used for Sprinklr LLM router HTTP calls.
 * <p>
 * Timeouts are explicit (per architecture.md) so hung router connections do not
 * block orchestrator threads indefinitely. Cookie auth is applied as a default header
 * when configured — the cookie value itself is never logged.
 */
@Configuration
public class LlmWebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(LlmWebClientConfig.class);

    @Bean(name = "llmWebClient")
    public WebClient llmWebClient(LlmProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs());

        log.info("[LLM] WebClient configured baseUrl={} connectTimeoutMs={} readTimeoutMs={} cookieConfigured={}",
                properties.getBaseUrl(),
                properties.getConnectTimeoutMs(),
                properties.getReadTimeoutMs(),
                properties.getCookie() != null && !properties.getCookie().isBlank());

        WebClient.Builder builder = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        if (properties.getCookie() != null && !properties.getCookie().isBlank()) {
            builder.defaultHeader(HttpHeaders.COOKIE, properties.getCookie());
        }

        return builder.build();
    }
}
