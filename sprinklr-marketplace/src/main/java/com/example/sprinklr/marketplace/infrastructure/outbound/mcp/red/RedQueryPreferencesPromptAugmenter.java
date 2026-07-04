package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds RED query allowlist markdown for the LLM system prompt when ES/Mongo query tools are in scope.
 */
@Component
public class RedQueryPreferencesPromptAugmenter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ES_SAMPLE = RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL;
    private static final String ES_EXECUTE = RedSampleQueryCacheKeyBuilder.ES_EXECUTE_TOOL;
    private static final String MONGO_SAMPLE = RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL;
    private static final String MONGO_EXECUTE = RedSampleQueryCacheKeyBuilder.MONGO_EXECUTE_TOOL;

    public RedQueryAllowlistContext build(
            List<McpTool> activeTools,
            RedQueryPreferences preferences,
            List<Message> history,
            String currentTurnUserMessageId
    ) {
        if (preferences == null || preferences.isEmpty() || activeTools == null || activeTools.isEmpty()) {
            return RedQueryAllowlistContext.empty();
        }

        boolean includeEs = hasRedQueryTool(activeTools, ES_SAMPLE, ES_EXECUTE);
        boolean includeMongo = hasRedQueryTool(activeTools, MONGO_SAMPLE, MONGO_EXECUTE);
        if (!includeEs && !includeMongo) {
            return RedQueryAllowlistContext.empty();
        }

        StringBuilder section = new StringBuilder();
        section.append("\n\n#### RED configured allowlists (use these values for serverType / collectionName)\n");
        section.append("If any required scope arg is still missing or ambiguous, respond with text only ")
                .append("(no tool calls) and ask the user — use the lists below as the options to offer.\n");

        if (includeEs && !preferences.elasticsearchServerTypes().isEmpty()) {
            section.append("\n**Elasticsearch serverType:** ")
                    .append(String.join(", ", preferences.elasticsearchServerTypes()))
                    .append('\n');
        }

        if (includeMongo && !preferences.mongoServerTypes().isEmpty()) {
            Set<String> detectedMongoServerTypes = detectMongoServerTypes(
                    preferences,
                    history,
                    currentTurnUserMessageId);
            List<RedQueryPreferences.MongoServerTypeConfig> mongoEntries = narrowMongoEntries(
                    preferences.mongoServerTypes(),
                    detectedMongoServerTypes);

            if (!mongoEntries.isEmpty()) {
                section.append("\n**Mongo (serverType → collections):**\n");
                for (RedQueryPreferences.MongoServerTypeConfig entry : mongoEntries) {
                    section.append("- ")
                            .append(entry.serverType())
                            .append(": ")
                            .append(String.join(", ", entry.collectionNames()))
                            .append('\n');
                }
            }
        }

        return section.length() == 0
                ? RedQueryAllowlistContext.empty()
                : new RedQueryAllowlistContext(section.toString().trim());
    }

    private static boolean hasRedQueryTool(List<McpTool> activeTools, String sampleTool, String executeTool) {
        for (McpTool tool : activeTools) {
            String bareName = bareToolName(tool.name());
            if (sampleTool.equals(bareName) || executeTool.equals(bareName)) {
                return true;
            }
        }
        return false;
    }

    private static String bareToolName(String fullyQualifiedName) {
        int dot = fullyQualifiedName.lastIndexOf('.');
        return dot >= 0 ? fullyQualifiedName.substring(dot + 1) : fullyQualifiedName;
    }

    private static List<RedQueryPreferences.MongoServerTypeConfig> narrowMongoEntries(
            List<RedQueryPreferences.MongoServerTypeConfig> configured,
            Set<String> detectedServerTypes
    ) {
        if (detectedServerTypes.size() == 1) {
            String serverType = detectedServerTypes.iterator().next();
            return configured.stream()
                    .filter(entry -> entry.serverType().equals(serverType))
                    .toList();
        }
        return configured;
    }

    private static Set<String> detectMongoServerTypes(
            RedQueryPreferences preferences,
            List<Message> history,
            String currentTurnUserMessageId
    ) {
        Set<String> configured = preferences.mongoServerTypes().stream()
                .map(RedQueryPreferences.MongoServerTypeConfig::serverType)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (configured.isEmpty()) {
            return Set.of();
        }

        Set<String> detected = new LinkedHashSet<>();
        for (String text : currentTurnTexts(history, currentTurnUserMessageId)) {
            for (String serverType : configured) {
                if (containsToken(text, serverType)) {
                    detected.add(serverType);
                }
            }
        }
        for (String argumentsJson : currentTurnToolArguments(history, currentTurnUserMessageId)) {
            String serverType = readJsonField(argumentsJson, "serverType");
            if (serverType != null && configured.contains(serverType)) {
                detected.add(serverType);
            }
        }
        return detected;
    }

    private static List<String> currentTurnTexts(List<Message> history, String currentTurnUserMessageId) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        boolean inCurrentTurn = currentTurnUserMessageId == null || currentTurnUserMessageId.isBlank();
        for (Message message : history) {
            if (!inCurrentTurn && currentTurnUserMessageId.equals(message.id())) {
                inCurrentTurn = true;
            }
            if (!inCurrentTurn) {
                continue;
            }
            if (message.content() != null && !message.content().isBlank()) {
                texts.add(message.content());
            }
        }
        return texts;
    }

    private static List<String> currentTurnToolArguments(List<Message> history, String currentTurnUserMessageId) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<String> arguments = new ArrayList<>();
        boolean inCurrentTurn = currentTurnUserMessageId == null || currentTurnUserMessageId.isBlank();
        for (Message message : history) {
            if (!inCurrentTurn && currentTurnUserMessageId.equals(message.id())) {
                inCurrentTurn = true;
            }
            if (!inCurrentTurn) {
                continue;
            }
            if (message.role() == MessageRole.ASSISTANT) {
                for (ToolCall toolCall : message.toolCalls()) {
                    if (toolCall.argumentsJson() != null && !toolCall.argumentsJson().isBlank()) {
                        arguments.add(toolCall.argumentsJson());
                    }
                }
            }
        }
        return arguments;
    }

    private static boolean containsToken(String text, String token) {
        if (text == null || text.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String upperText = text.toUpperCase(Locale.ROOT);
        String upperToken = token.toUpperCase(Locale.ROOT);
        int index = upperText.indexOf(upperToken);
        while (index >= 0) {
            boolean startOk = index == 0 || !Character.isLetterOrDigit(upperText.charAt(index - 1));
            int end = index + upperToken.length();
            boolean endOk = end >= upperText.length() || !Character.isLetterOrDigit(upperText.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            index = upperText.indexOf(upperToken, index + 1);
        }
        return false;
    }

    private static String readJsonField(String argumentsJson, String fieldName) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            JsonNode value = root.path(fieldName);
            if (value.isMissingNode() || value.isNull() || !value.isTextual()) {
                return null;
            }
            String text = value.asText("").trim();
            return text.isEmpty() ? null : text;
        } catch (Exception ignored) {
            return null;
        }
    }
}
