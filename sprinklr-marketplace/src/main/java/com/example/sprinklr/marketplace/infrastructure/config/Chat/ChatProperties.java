package com.example.sprinklr.marketplace.infrastructure.config.Chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * Chat orchestration settings (history window, tool result retention).
 */
@Data
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /**
     * Maximum user prompts (turns) included in LLM context, including the current turn.
     */
    private int historyTurnLimit = 10;
}
