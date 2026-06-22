package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches Jira field value shapes from the latest {@code getJiraIssueTypeMetaWithFields}
 * summary per MCP connection so create argument normalization can respect array vs object.
 */
@Component
public class JiraIssueTypeFieldShapeCache {

    private final Map<String, Map<String, String>> shapesByConnection = new ConcurrentHashMap<>();

    public void put(String connectionId, Map<String, String> fieldShapes) {
        if (connectionId == null || connectionId.isBlank() || fieldShapes == null || fieldShapes.isEmpty()) {
            return;
        }
        shapesByConnection.put(connectionId, Map.copyOf(fieldShapes));
    }

    public String valueShape(String connectionId, String fieldKey) {
        if (connectionId == null || fieldKey == null) {
            return null;
        }
        Map<String, String> shapes = shapesByConnection.get(connectionId);
        if (shapes == null) {
            return null;
        }
        String shape = shapes.get(fieldKey);
        if (shape != null) {
            return shape;
        }
        return shapes.get(fieldKey.toLowerCase());
    }
}
