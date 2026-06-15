package com.example.sprinklr.marketplace.infrastructure.outbound.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI-style function tool definition sent to the router when MCP tools are whitelisted.
 */
public record LlmApiTool(
        String type,
        LlmApiFunction function
) {

    public static LlmApiTool function(String name, String description, Object parameters) {
        return new LlmApiTool("function", new LlmApiFunction(name, description, parameters));
    }

    public record LlmApiFunction(
            String name,
            String description,
            Object parameters
    ) {
    }
}

/**
 * example of how it looks in the payload
 * {
  "type": "function",
  "function": {
    "name": "get_weather",
    "description": "Fetches current weather for a city",
    "parameters": {
      "type": "object",
      "properties": {
        "city": {
          "type": "string",
          "description": "The city name, e.g., San Francisco"
        }
      },
      "required": ["city"]
    }
  }
}
 */
