package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * RED sample/execute query pair mappings for runtime workflow nudges and preflight guards.
 * <p>
 * Chat-time tool scoping uses the connect-time {@code staticDependencyGraph} on the RED catalog entry;
 * this class is not used for router expansion.
 */
@Component
public class RedQueryToolSelectionSupport {

    private static final Map<String, String> SAMPLE_FOR_EXECUTE = Map.of(
            "red.red_execute_mongo_query", "red.red_sample_mongo_query",
            "red.red_execute_elastic_search_query", "red.red_sample_elasticsearch_query"
    );

    private static final Map<String, String> EXECUTE_FOR_SAMPLE = invert(SAMPLE_FOR_EXECUTE);

    public Optional<String> sampleToolForExecute(String fullyQualifiedToolName) {
        return Optional.ofNullable(SAMPLE_FOR_EXECUTE.get(fullyQualifiedToolName));
    }

    public Optional<String> executeToolForSample(String fullyQualifiedToolName) {
        return Optional.ofNullable(EXECUTE_FOR_SAMPLE.get(fullyQualifiedToolName));
    }

    public boolean isQueryPairTool(String fullyQualifiedToolName) {
        return SAMPLE_FOR_EXECUTE.containsKey(fullyQualifiedToolName)
                || EXECUTE_FOR_SAMPLE.containsKey(fullyQualifiedToolName);
    }

    /**
     * Returns sample then execute when either half of a RED query pair is selected; otherwise the tool itself.
     */
    public List<String> expandQueryPair(String fullyQualifiedToolName, Set<String> availableToolNames) {
        if (SAMPLE_FOR_EXECUTE.containsKey(fullyQualifiedToolName)) {
            return pairOrder(
                    SAMPLE_FOR_EXECUTE.get(fullyQualifiedToolName),
                    fullyQualifiedToolName,
                    availableToolNames);
        }
        if (EXECUTE_FOR_SAMPLE.containsKey(fullyQualifiedToolName)) {
            return pairOrder(
                    fullyQualifiedToolName,
                    EXECUTE_FOR_SAMPLE.get(fullyQualifiedToolName),
                    availableToolNames);
        }
        return List.of(fullyQualifiedToolName);
    }

    public static Map<String, String> sampleForExecuteMap() {
        return new LinkedHashMap<>(SAMPLE_FOR_EXECUTE);
    }

    private static List<String> pairOrder(
            String sampleTool,
            String executeTool,
            Set<String> availableToolNames
    ) {
        List<String> ordered = new java.util.ArrayList<>(2);
        if (availableToolNames.contains(sampleTool)) {
            ordered.add(sampleTool);
        }
        if (availableToolNames.contains(executeTool) && !executeTool.equals(sampleTool)) {
            ordered.add(executeTool);
        }
        return ordered.isEmpty() ? List.of(sampleTool) : ordered;
    }

    private static Map<String, String> invert(Map<String, String> sampleForExecute) {
        Map<String, String> inverted = new LinkedHashMap<>();
        sampleForExecute.forEach((execute, sample) -> inverted.put(sample, execute));
        return Map.copyOf(inverted);
    }
}
