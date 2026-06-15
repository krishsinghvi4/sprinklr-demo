package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto.LlmApiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Converts whitelisted {@link McpTool} domain records into OpenAI-style {@code tools} JSON
 * for the Sprinklr router. Empty when no MCP servers are configured — the orchestrator
 * currently passes an empty list; this mapper is ready for when MCP is wired.
 */
@Component
public class LlmToolMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public LlmToolMapper() {
    }

    /**
     * @return API tool definitions; empty list when no tools are whitelisted.
     */
    public List<LlmApiTool> toApiTools(List<McpTool> tools) {
        return tools.stream()
                .map(this::toApiTool)
                .toList();
    }

    /**
     * Router expects {@code "none"} when no tools are sent (text-only chat)
     * and {@code "auto"} when the model may choose tools.
     */
    public String resolveToolChoice(List<McpTool> tools) {
        return tools.isEmpty() ? "none" : "auto";
    }

    private LlmApiTool toApiTool(McpTool tool) {
        Object parameters = parseParameters(tool.inputSchemaJson());
        return LlmApiTool.function(tool.name(), tool.description(), parameters);
    }

  /**
   * inputSchemaJson is stored as a JSON object string in McpTool; parse it for the router payload.
   * On parse failure, fall back to an empty object so a bad schema does not break the whole request.
   */
    private Object parseParameters(String inputSchemaJson) {
        try {
            return OBJECT_MAPPER.readValue(inputSchemaJson, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }
}
