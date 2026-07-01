package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.domain.model.insights.WidgetBlock;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WidgetBlockParser {

    private static final Pattern WIDGET_FENCE = Pattern.compile("```widget\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WidgetBlockParser() {
    }

    public static Optional<WidgetBlock> parseFromContent(String assistantContent) {
        if (assistantContent == null || assistantContent.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = WIDGET_FENCE.matcher(assistantContent);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return parseJson(matcher.group(1).trim());
    }

    public static Optional<WidgetBlock> parseJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            int version = root.path("version").asInt(-1);
            JsonNode widgetsNode = root.path("widgets");
            if (!widgetsNode.isArray()) {
                return Optional.empty();
            }
            List<WidgetSpec> widgets = new ArrayList<>();
            for (JsonNode node : widgetsNode) {
                String id = textOrNull(node, "id");
                String type = textOrNull(node, "type");
                String title = textOrNull(node, "title");
                String description = textOrNull(node, "description");
                JsonNode dataNode = node.path("data");
                if (id == null || type == null || title == null || dataNode.isMissingNode()) {
                    continue;
                }
                Map<String, Object> data = MAPPER.convertValue(dataNode, new TypeReference<>() {});
                widgets.add(new WidgetSpec(id, type, title, description, data));
            }
            if (widgets.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new WidgetBlock(version, widgets));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public static String extractNarrative(String assistantContent) {
        if (assistantContent == null) {
            return "";
        }
        return WIDGET_FENCE.matcher(assistantContent).replaceAll("").trim();
    }

    public static boolean hasWidgetFence(String assistantContent) {
        return assistantContent != null && WIDGET_FENCE.matcher(assistantContent).find();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }
}
