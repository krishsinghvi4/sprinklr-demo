package com.example.sprinklr.marketplace;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.HttpMcpClientAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.McpCircuitBreakerFactory;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.CompositeMcpToolResultPostProcessor;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.invoke.McpInvocationPreparer;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.CompositeMcpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpMcpClientAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void delegatesPreparationToInvocationPreparer() throws Exception {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);
        McpInvocationPreparer invocationPreparer = mock(McpInvocationPreparer.class);
        CompositeMcpToolResultPostProcessor resultPostProcessor =
                new CompositeMcpToolResultPostProcessor(List.of());
        CompositeMcpLocalToolExtension localToolExtension =
                new CompositeMcpLocalToolExtension(List.of());

        McpConnectionDocument connection = new McpConnectionDocument(
                "conn-1",
                "user-1",
                "atlassian-jira",
                "jira",
                "encrypted",
                "session-1",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null,
                null,
                null,
                null
        );

        var catalogEntry = McpCatalogTestFixtures.jiraEntry();
        var prepared = new McpInvocationPreparer.PreparedInvocation(
                catalogEntry,
                Map.of("accessToken", "new-token"),
                Map.of("Authorization", "Bearer new-token"),
                new StreamableHttpMcpClient.McpSession("session-1", "2025-03-26"),
                "{}"
        );

        when(repository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-1")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(invocationPreparer.prepare(connection, "jira.search", "{}"))
                .thenReturn(prepared);
        when(mcpClient.callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(OBJECT_MAPPER.readTree("{\"content\":\"ok\"}"));

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                mcpClient,
                circuitBreakerFactory,
                invocationPreparer,
                resultPostProcessor,
                localToolExtension
        );

        var result = adapter.invoke(new McpInvocation("conn-1", "jira.search", "{}", "call-1"));
        assertTrue(result.success());
        verify(invocationPreparer).prepare(connection, "jira.search", "{}");
    }

    @Test
    void routesLocalToolsWithoutCallingRemoteMcp() {
        McpConnectionRepository repository = mock(McpConnectionRepository.class);
        StreamableHttpMcpClient mcpClient = mock(StreamableHttpMcpClient.class);
        McpCircuitBreakerFactory circuitBreakerFactory = mock(McpCircuitBreakerFactory.class);
        McpInvocationPreparer invocationPreparer = mock(McpInvocationPreparer.class);
        CompositeMcpToolResultPostProcessor resultPostProcessor =
                new CompositeMcpToolResultPostProcessor(List.of());

        McpLocalToolExtension localHandler = mock(McpLocalToolExtension.class);
        when(localHandler.supports(any())).thenReturn(true);
        when(localHandler.handles(any(), anyString())).thenReturn(true);
        when(localHandler.invoke(any(), anyString(), any())).thenReturn("{\"issueKey\":\"ITOPS-1\"}");
        CompositeMcpLocalToolExtension localToolExtension =
                new CompositeMcpLocalToolExtension(List.of(localHandler));

        McpConnectionDocument connection = new McpConnectionDocument(
                "conn-1",
                "user-1",
                "atlassian-jira",
                "jira",
                "encrypted",
                "session-1",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null,
                null,
                null,
                null
        );

        var catalogEntry = McpCatalogTestFixtures.jiraEntry();
        var prepared = new McpInvocationPreparer.PreparedInvocation(
                catalogEntry,
                Map.of("accessToken", "token"),
                Map.of("Authorization", "Bearer token"),
                new StreamableHttpMcpClient.McpSession("session-1", "2025-03-26"),
                "{\"cloudId\":\"cloud-1\",\"issueKey\":\"ITOPS-1\"}"
        );

        when(repository.findById("conn-1")).thenReturn(Optional.of(connection));
        when(circuitBreakerFactory.forConnection("conn-1")).thenReturn(CircuitBreaker.ofDefaults("mcp"));
        when(invocationPreparer.prepare(connection, "getJiraIssueChangelog", prepared.argumentsJson()))
                .thenReturn(prepared);

        HttpMcpClientAdapter adapter = new HttpMcpClientAdapter(
                repository,
                mcpClient,
                circuitBreakerFactory,
                invocationPreparer,
                resultPostProcessor,
                localToolExtension
        );

        var result = adapter.invoke(new McpInvocation(
                "conn-1",
                "getJiraIssueChangelog",
                prepared.argumentsJson(),
                "call-2"
        ));

        assertTrue(result.success());
        assertEquals("{\"issueKey\":\"ITOPS-1\"}", result.content());
        verify(mcpClient, never()).callTool(anyString(), anyMap(), anyString(), anyString(), anyString(), anyString());
    }
}
