package com.example.sprinklr.marketplace.application.service.insights;

import com.example.sprinklr.marketplace.domain.model.insights.WidgetBlock;
import com.example.sprinklr.marketplace.domain.model.insights.WidgetSpec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WidgetBlockParser {

    private static final Pattern WIDGET_FENCE = Pattern.compile("```widget\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern BULLET_LINE = Pattern.compile("(?m)^\\s*[-*]\\s+.*$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int NARRATIVE_MAX_CHARS = 280;

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
        return truncateNarrative(WIDGET_FENCE.matcher(assistantContent).replaceAll("").trim());
    }

    public static String truncateNarrative(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = BULLET_LINE.matcher(text).replaceAll("").trim().replaceAll("\\s+", " ");
        if (normalized.length() <= NARRATIVE_MAX_CHARS) {
            return normalized;
        }
        return normalized.substring(0, NARRATIVE_MAX_CHARS - 3) + "...";
    }

    public static String buildMinimalAssistantContent(String narrative, List<WidgetSpec> widgets) {
        String summary = truncateNarrative(narrative);
        String widgetJson = toWidgetFenceJson(widgets);
        if (summary.isBlank()) {
            return widgetJson;
        }
        return summary + "\n\n" + widgetJson;
    }

    public static String toWidgetFenceJson(List<WidgetSpec> widgets) {
        try {
            List<Map<String, Object>> widgetMaps = new ArrayList<>();
            for (WidgetSpec widget : widgets) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", widget.id());
                map.put("type", widget.type());
                map.put("title", widget.title());
                if (widget.description() != null && !widget.description().isBlank()) {
                    map.put("description", widget.description());
                }
                map.put("data", widget.data());
                widgetMaps.add(map);
            }
            Map<String, Object> block = Map.of("version", 1, "widgets", widgetMaps);
            return "```widget\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(block) + "\n```";
        } catch (Exception exception) {
            return "```widget\n{\"version\":1,\"widgets\":[]}\n```";
        }
    }

    public static NormalizedTurnContent normalizeTurnContent(String assistantContent, List<WidgetSpec> fallbackWidgets) {
        List<WidgetSpec> widgets = parseFromContent(assistantContent)
                .map(WidgetBlock::widgets)
                .orElse(fallbackWidgets != null ? fallbackWidgets : List.of());
        String rawNarrative = assistantContent == null ? "" : WIDGET_FENCE.matcher(assistantContent).replaceAll("").trim();
        String narrative = truncateNarrative(rawNarrative);
        String minimalContent = buildMinimalAssistantContent(narrative, widgets);
        return new NormalizedTurnContent(narrative, minimalContent, widgets);
    }

    public record NormalizedTurnContent(String narrative, String assistantContent, List<WidgetSpec> widgets) {}

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
