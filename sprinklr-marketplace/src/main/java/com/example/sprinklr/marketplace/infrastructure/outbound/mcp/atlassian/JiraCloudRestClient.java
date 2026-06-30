package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Direct Jira Cloud REST client for local tool extensions that the official MCP does not expose.
 */
@Component
public class JiraCloudRestClient {

    private static final Logger log = LoggerFactory.getLogger(JiraCloudRestClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int PAGE_SIZE = 100;

    private final WebClient webClient;

    public JiraCloudRestClient(@Qualifier("mcpWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public JsonNode fetchIssueChangelog(String cloudId, String issueKey, String accessToken, int maxResults) {
        if (cloudId == null || cloudId.isBlank()) {
            throw new McpInvocationException("cloudId is required", "Missing cloudId");
        }
        if (issueKey == null || issueKey.isBlank()) {
            throw new McpInvocationException("issueKey is required", "Missing issueKey");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new McpInvocationException("Jira OAuth token is missing", "Missing accessToken");
        }

        int remaining = Math.max(1, maxResults);
        int startAt = 0;
        ArrayNode mergedHistories = OBJECT_MAPPER.createArrayNode();
        int reportedTotal = 0;
        boolean isLast = false;

        while (remaining > 0 && !isLast) {
            int pageSize = Math.min(PAGE_SIZE, remaining);
            JsonNode page = fetchChangelogPage(cloudId, issueKey, accessToken, startAt, pageSize);
            reportedTotal = page.path("total").asInt(reportedTotal);
            isLast = page.path("isLast").asBoolean(true);

            JsonNode histories = page.path("values");
            if (!histories.isArray()) {
                histories = page.path("histories");
            }
            if (histories.isArray()) {
                histories.forEach(mergedHistories::add);
                remaining -= histories.size();
                startAt += histories.size();
            } else {
                break;
            }
            if (histories.isEmpty()) {
                break;
            }
        }

        ObjectNode merged = OBJECT_MAPPER.createObjectNode();
        merged.put("total", reportedTotal > 0 ? reportedTotal : mergedHistories.size());
        merged.put("isLast", isLast || mergedHistories.size() >= reportedTotal);
        merged.set("histories", mergedHistories);
        return merged;
    }

    private JsonNode fetchChangelogPage(
            String cloudId,
            String issueKey,
            String accessToken,
            int startAt,
            int maxResults
    ) {
        String url = "https://api.atlassian.com/ex/jira/" + cloudId.trim()
                + "/rest/api/3/issue/" + issueKey.trim() + "/changelog"
                + "?startAt=" + startAt + "&maxResults=" + maxResults;

        try {
            String body = webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken.trim())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .map(message -> new McpInvocationException(
                                    "Jira changelog request failed: " + message,
                                    "HTTP " + response.statusCode() + " url=" + url
                            )))
                    .bodyToMono(String.class)
                    .block();

            return OBJECT_MAPPER.readTree(body);
        } catch (McpInvocationException exception) {
            throw exception;
        } catch (WebClientResponseException exception) {
            log.warn("[JiraREST] Changelog failed issueKey={} status={}: {}",
                    issueKey, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new McpInvocationException(
                    "Jira changelog request failed: " + exception.getResponseBodyAsString(),
                    exception.getMessage()
            );
        } catch (Exception exception) {
            log.warn("[JiraREST] Changelog failed issueKey={}: {}", issueKey, exception.getMessage());
            throw new McpInvocationException(
                    "Jira changelog request failed — please try again",
                    exception.getMessage()
            );
        }
    }
}
