package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InsightsExpansionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final LlmPort llmPort;

    public InsightsExpansionService(LlmPort llmPort) {
        this.llmPort = llmPort;
    }

    public String expandWidget(
            String userId,
            WidgetSpec widget,
            String toolResultSnapshot,
            String userFocus
    ) {
        String widgetJson;
        try {
            widgetJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(widget);
        } catch (Exception exception) {
            widgetJson = widget.toString();
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("Provide extended insights for this analytics widget. ");
        prompt.append("Ground every claim in the tool data below — do not hallucinate.\n\n");
        prompt.append("## Widget\n```json\n").append(widgetJson).append("\n```\n\n");
        if (toolResultSnapshot != null && !toolResultSnapshot.isBlank()) {
            prompt.append("## Source tool data\n```json\n").append(truncate(toolResultSnapshot, 12000)).append("\n```\n\n");
        }
        if (userFocus != null && !userFocus.isBlank()) {
            prompt.append("## User focus\n").append(userFocus).append("\n\n");
        }
        prompt.append("Respond with markdown analysis: key patterns, notable events, and actionable observations.");

        Message userMessage = new Message(
                "expand-" + UUID.randomUUID(),
                "insights-expand",
                MessageRole.USER,
                prompt.toString(),
                List.of(),
                List.of(),
                Instant.now()
        );

        LlmResponse response = llmPort.complete(new LlmRequest(
                prompt.toString(),
                List.of(userMessage),
                List.of(),
                userMessage.id(),
                userId,
                "insights-expand"
        ));

        return response.content() != null ? response.content() : "Unable to generate extended insights.";
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }
}
