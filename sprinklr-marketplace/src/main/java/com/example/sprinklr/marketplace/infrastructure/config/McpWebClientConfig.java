package com.example.sprinklr.marketplace.infrastructure.config;

import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration
public class McpWebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpWebClientConfig.class);

    @Bean(name = "mcpWebClient")
    public WebClient mcpWebClient(McpProperties properties) {
        ConnectionProvider connectionProvider = ConnectionProvider.builder("mcp-clients")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(55))
                .maxLifeTime(Duration.ofMinutes(10))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getConnectTimeoutMs());

        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(properties.getMaxResponseBytes()))
                .build();

        log.info("[MCP] WebClient configured connectTimeoutMs={} readTimeoutMs={} maxResponseBytes={}",
                properties.getConnectTimeoutMs(), properties.getReadTimeoutMs(),
                properties.getMaxResponseBytes());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(exchangeStrategies)
                .build();
    }
}
