package com.example.sprinklr.marketplace;

import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelOption;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StreamableHttpMcpClientTest {

    @Test
    void initializeAndListToolsAgainstAtlassian() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        StreamableHttpMcpClient client = new StreamableHttpMcpClient(webClient);
        String encoded = Base64.getEncoder()
                .encodeToString("test@example.com:fake-token".getBytes(StandardCharsets.UTF_8));

        StreamableHttpMcpClient.McpSession session = client.initialize(
                "https://mcp.atlassian.com/v1/mcp/authv2",
                Map.of("Authorization", "Basic " + encoded)
        );

        assertNotNull(session.sessionId());
        assertNotNull(session.protocolVersion());

        var toolsResult = client.listTools(
                "https://mcp.atlassian.com/v1/mcp/authv2",
                Map.of("Authorization", "Basic " + encoded),
                session
        );

        assertNotNull(toolsResult);
        assertFalse(toolsResult.path("tools").isEmpty());
    }

    @Test
    void objectNodeBodyValueReturnsBadRequest() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(60))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);

        WebClient webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = mapper.createObjectNode();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", "initialize");
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        params.set("capabilities", mapper.createObjectNode());
        ObjectNode clientInfo = mapper.createObjectNode();
        clientInfo.put("name", "test");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        body.set("params", params);

        String encoded = Base64.getEncoder()
                .encodeToString("test@example.com:fake-token".getBytes(StandardCharsets.UTF_8));

        var status = webClient.post()
                .uri("https://mcp.atlassian.com/v1/mcp/authv2")
                .headers(h -> {
                    h.setContentType(MediaType.APPLICATION_JSON);
                    h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
                    h.set("Authorization", "Basic " + encoded);
                })
                .bodyValue(body)
                .exchangeToMono(response -> reactor.core.publisher.Mono.just(response.statusCode().value()))
                .block();

        // Documents the WebClient quirk: ObjectNode body encoding is rejected by Atlassian MCP.
        org.junit.jupiter.api.Assertions.assertEquals(400, status);
    }
}
