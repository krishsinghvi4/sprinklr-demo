package com.example.sprinklr.marketplace.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chat orchestration settings (history window, tool result retention).
 */
@Data
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /**
     * Maximum user prompts (turns) included in LLM context, including the current turn.
     */
    private int historyTurnLimit = 5;
}
