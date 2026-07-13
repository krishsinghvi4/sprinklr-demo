package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.teams;

import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.exceptions.McpInvocationException;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolExtension;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolInvocationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TeamsMessageSearchLocalTool implements McpLocalToolExtension {

    private static final Logger log = LoggerFactory.getLogger(TeamsMessageSearchLocalTool.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEAMS_PREFIX = "teams";
    private static final String TOOL_NAME = "search_channel_messages";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;
    private static final String DEFAULT_INDEX = "teams_messages";

    private final String configuredEsUrl;
    private final String configuredEsApiKey;
    private final String configuredEsIndex;

    public TeamsMessageSearchLocalTool(
            @Value("${app.teams.es.url}") String configuredEsUrl,
            @Value("${app.teams.es.api-key}") String configuredEsApiKey,
            @Value("${app.teams.es.index:teams_messages}") String configuredEsIndex
    ) {
        this.configuredEsUrl = configuredEsUrl;
        this.configuredEsApiKey = configuredEsApiKey;
        this.configuredEsIndex = configuredEsIndex;
    }

    private static final String INPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "query": {
                  "type": "string",
                  "description": "Full-text search in message body"
                },
                "channelName": {
                  "type": "string",
                  "description": "Filter to a specific channel name"
                },
                "teamName": {
                  "type": "string",
                  "description": "Filter to a specific team name"
                },
                "mentionedUser": {
                  "type": "string",
                  "description": "Filter messages mentioning this display name"
                },
                "since": {
                  "type": "string",
                  "description": "ISO-8601 date — messages after this time"
                },
                "until": {
                  "type": "string",
                  "description": "ISO-8601 date — messages before this time"
                },
                "limit": {
                  "type": "integer",
                  "description": "Max results to return (default 20, max 50)"
                }
              }
            }
            """;

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return TEAMS_PREFIX.equals(entry.serverIdPrefix());
    }

    @Override
    public List<McpTool> toolDefinitions(McpCatalogEntry entry, String connectionId) {
        if (!supports(entry)) {
            return List.of();
        }
        return List.of(new McpTool(
                entry.serverIdPrefix() + "." + TOOL_NAME,
                "Search indexed Microsoft Teams channel messages for this user.",
                connectionId,
                INPUT_SCHEMA
        ));
    }

    @Override
    public boolean handles(McpCatalogEntry entry, String bareToolName) {
        return supports(entry) && TOOL_NAME.equals(bareToolName);
    }

    @Override
    public String invoke(McpCatalogEntry entry, String bareToolName, McpLocalToolInvocationContext context) {
        SearchArgs args = parseArgs(context.argumentsJson());
        String userId = context.connection().userId();
        Map<String, String> credentials = context.credentials();

        String esUrl = resolveEsSetting(configuredEsUrl, credentials.get("esUrl"), "esUrl");
        String esApiKey = resolveEsSetting(configuredEsApiKey, credentials.get("esApiKey"), "esApiKey");
        String esIndex = resolveEsSetting(configuredEsIndex, credentials.get("esIndex"), "esIndex");
        if (esIndex.isBlank()) {
            esIndex = DEFAULT_INDEX;
        }

        log.info("[TeamsSearch] userId={} channelName={} query={}",
                userId, args.channelName(), args.query());

        ObjectNode searchBody = buildSearchQuery(userId, args);
        JsonNode response = executeSearch(esUrl, esApiKey, esIndex, searchBody);
        return formatHits(response);
    }

    private ObjectNode buildSearchQuery(String userId, SearchArgs args) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("size", args.limit());

        ObjectNode bool = OBJECT_MAPPER.createObjectNode();
        ArrayNode filter = OBJECT_MAPPER.createArrayNode();
        filter.add(termFilter("userId", userId));

        if (args.channelName() != null && !args.channelName().isBlank()) {
            filter.add(termFilter("channelName", args.channelName()));
        }
        if (args.teamName() != null && !args.teamName().isBlank()) {
            filter.add(termFilter("teamName", args.teamName()));
        }
        if (args.mentionedUser() != null && !args.mentionedUser().isBlank()) {
            filter.add(termFilter("mentions.displayName", args.mentionedUser()));
        }
        if (args.since() != null || args.until() != null) {
            filter.add(rangeFilter("createdAt", args.since(), args.until()));
        }

        bool.set("filter", filter);

        if (args.query() != null && !args.query().isBlank()) {
            ObjectNode must = OBJECT_MAPPER.createObjectNode();
            ObjectNode match = OBJECT_MAPPER.createObjectNode();
            match.put("bodyPlainText", args.query());
            must.set("match", match);
            bool.set("must", OBJECT_MAPPER.createArrayNode().add(must));
        }

        ObjectNode query = OBJECT_MAPPER.createObjectNode();
        query.set("bool", bool);
        root.set("query", query);

        ObjectNode sortField = OBJECT_MAPPER.createObjectNode();
        sortField.put("order", "desc");
        ObjectNode sortEntry = OBJECT_MAPPER.createObjectNode();
        sortEntry.set("createdAt", sortField);
        ArrayNode sort = OBJECT_MAPPER.createArrayNode();
        sort.add(sortEntry);
        root.set("sort", sort);
        return root;
    }

    private static ObjectNode termFilter(String field, String value) {
        ObjectNode termValue = OBJECT_MAPPER.createObjectNode();
        termValue.put("value", value);
        ObjectNode term = OBJECT_MAPPER.createObjectNode();
        term.set(keywordFieldName(field), termValue);
        ObjectNode wrapper = OBJECT_MAPPER.createObjectNode();
        wrapper.set("term", term);
        return wrapper;
    }

    private static String keywordFieldName(String field) {
        return switch (field) {
            case "userId", "channelName", "teamName" -> field + ".keyword";
            case "mentions.displayName" -> "mentions.displayName.keyword";
            default -> field;
        };
    }

    private static ObjectNode rangeFilter(String field, String since, String until) {
        ObjectNode rangeField = OBJECT_MAPPER.createObjectNode();
        if (since != null && !since.isBlank()) {
            rangeField.put("gte", since);
        }
        if (until != null && !until.isBlank()) {
            rangeField.put("lte", until);
        }
        ObjectNode range = OBJECT_MAPPER.createObjectNode();
        range.set(field, rangeField);
        ObjectNode wrapper = OBJECT_MAPPER.createObjectNode();
        wrapper.set("range", range);
        return wrapper;
    }

    private JsonNode executeSearch(String esUrl, String esApiKey, String esIndex, ObjectNode searchBody) {
        String baseUrl = esUrl.endsWith("/") ? esUrl.substring(0, esUrl.length() - 1) : esUrl;
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(searchBody);
            String responseBody = WebClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "ApiKey " + esApiKey)
                    .build()
                    .post()
                    .uri("/" + esIndex + "/_search")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode response = OBJECT_MAPPER.readTree(responseBody);
            return response;
        } catch (WebClientResponseException exception) {
            String responseBody = exception.getResponseBodyAsString();
            throw new McpInvocationException(
                    "Failed to search Teams messages in Elasticsearch — verify ES URL, API key, and index name",
                    exception.getStatusCode() + " " + exception.getMessage()
                            + (responseBody == null || responseBody.isBlank() ? "" : " — " + responseBody)
            );
        } catch (Exception exception) {
            throw new McpInvocationException(
                    "Failed to search Teams messages in Elasticsearch — verify ES URL, API key, and index name",
                    exception.getMessage()
            );
        }
    }

    private String formatHits(JsonNode response) {
        JsonNode hits = response.path("hits").path("hits");
        List<Map<String, Object>> results = new ArrayList<>();
        if (hits.isArray()) {
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("senderDisplayName", source.path("senderDisplayName").asText(""));
                row.put("channelName", source.path("channelName").asText(""));
                row.put("teamName", source.path("teamName").asText(""));
                row.put("createdAt", source.path("createdAt").asText(""));
                row.put("bodyPlainText", source.path("bodyPlainText").asText(""));
                row.put("subject", source.path("subject").asText(""));
                if (source.has("mentions")) {
                    row.put("mentions", source.get("mentions"));
                }
                results.add(row);
            }
        }
        ObjectNode output = OBJECT_MAPPER.createObjectNode();
        output.put("total", response.path("hits").path("total").path("value").asInt(results.size()));
        output.set("messages", OBJECT_MAPPER.valueToTree(results));
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception exception) {
            return output.toString();
        }
    }

    private SearchArgs parseArgs(String argumentsJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            int limit = root.has("limit") ? root.path("limit").asInt(DEFAULT_LIMIT) : DEFAULT_LIMIT;
            limit = Math.min(Math.max(limit, 1), MAX_LIMIT);
            return new SearchArgs(
                    root.path("query").asText(null),
                    root.path("channelName").asText(null),
                    root.path("teamName").asText(null),
                    root.path("mentionedUser").asText(null),
                    root.path("since").asText(null),
                    root.path("until").asText(null),
                    limit
            );
        } catch (Exception exception) {
            throw new McpInvocationException("Invalid arguments for search_channel_messages", exception.getMessage());
        }
    }

    private static String resolveEsSetting(String configuredValue, String storedValue, String key) {
        if (configuredValue != null && !configuredValue.isBlank()) {
            return configuredValue.trim();
        }
        if (storedValue != null && !storedValue.isBlank()) {
            return storedValue.trim();
        }
        throw new McpInvocationException(
                "Teams Messages connection is missing " + key + " — set ES_* env vars or reconnect from Profile",
                "Missing credential " + key
        );
    }

    private record SearchArgs(
            String query,
            String channelName,
            String teamName,
            String mentionedUser,
            String since,
            String until,
            int limit
    ) {
    }
}
