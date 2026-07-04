package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedQueryPreferencesPromptAugmenterTest {

    private final RedQueryPreferencesPromptAugmenter augmenter = new RedQueryPreferencesPromptAugmenter();

    private final RedQueryPreferences preferences = new RedQueryPreferences(
            List.of("AUDIENCE_CONTAINER", "AUDIT_LOGS"),
            List.of(
                    new RedQueryPreferences.MongoServerTypeConfig("PAID", List.of("paidInitiative", "adSet")),
                    new RedQueryPreferences.MongoServerTypeConfig("DEFAULT", List.of("audience", "campaign"))
            )
    );

    @Test
    void skipsInjectionWhenQueryToolsAbsent() {
        RedQueryAllowlistContext context = augmenter.build(
                List.of(redTool("red.red_ping")),
                preferences,
                List.of(),
                null);

        assertFalse(context.hasContent());
    }

    @Test
    void includesEsAndMongoSectionsWhenBothQueryToolsScoped() {
        RedQueryAllowlistContext context = augmenter.build(
                List.of(
                        redTool("red.red_sample_elasticsearch_query"),
                        redTool("red.red_sample_mongo_query")),
                preferences,
                List.of(),
                null);

        assertTrue(context.hasContent());
        assertTrue(context.markdownSection().contains("Elasticsearch serverType"));
        assertTrue(context.markdownSection().contains("AUDIENCE_CONTAINER"));
        assertTrue(context.markdownSection().contains("PAID: paidInitiative, adSet"));
        assertTrue(context.markdownSection().contains("DEFAULT: audience, campaign"));
    }

    @Test
    void narrowsMongoCollectionsWhenSingleServerTypeDetectedInUserMessage() {
        Message userMessage = new Message(
                "msg-1",
                "conv-1",
                MessageRole.USER,
                "Use PAID for partner 190",
                List.of(),
                List.of(),
                Instant.now());

        RedQueryAllowlistContext context = augmenter.build(
                List.of(redTool("red.red_execute_mongo_query")),
                preferences,
                List.of(userMessage),
                "msg-1");

        assertTrue(context.markdownSection().contains("PAID: paidInitiative, adSet"));
        assertFalse(context.markdownSection().contains("DEFAULT: audience, campaign"));
    }

    @Test
    void detectsServerTypeFromSameTurnToolArguments() {
        Message assistantMessage = new Message(
                "msg-2",
                "conv-1",
                MessageRole.ASSISTANT,
                null,
                List.of(new ToolCall(
                        "tc-1",
                        "red.red_sample_mongo_query",
                        "{\"serverType\":\"PAID\",\"partnerId\":\"190\"}")),
                List.of(),
                Instant.now());

        RedQueryAllowlistContext context = augmenter.build(
                List.of(redTool("red.red_execute_mongo_query")),
                preferences,
                List.of(userMessage("msg-1"), assistantMessage),
                "msg-1");

        assertTrue(context.markdownSection().contains("PAID: paidInitiative, adSet"));
        assertFalse(context.markdownSection().contains("DEFAULT: audience, campaign"));
    }

    private static Message userMessage(String id) {
        return new Message(
                id,
                "conv-1",
                MessageRole.USER,
                "fetch data",
                List.of(),
                List.of(),
                Instant.now());
    }

    private static McpTool redTool(String name) {
        return new McpTool(name, "desc", "conn-red", "{\"type\":\"object\"}");
    }
}
