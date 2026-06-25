package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.ToolRouterResult;

import java.util.List;

/**
 * Lightweight tool router: given the user's prompt and a compact catalog of their connected tools
 * (names + descriptions only, no schemas), selects the handful of tools most relevant to this turn.
 * <p>
 * This is the cheap "stage 1" LLM call. The deterministic expander then adds prerequisites and the
 * agent LLM (stage 2) only ever sees the resulting small set with full schemas.
 */
public interface ToolRouterPort {

    /**
     * @param userPrompt       the latest user message
     * @param recentHistory    a short slice of recent conversation turns for context
     * @param availableTools   the user's connected tools (only name + description are used)
     * @param maxPrimaryTools  soft cap on how many primary tools the router should pick
     * @return selection result with an explicit outcome; {@link com.example.sprinklr.marketplace.domain.model.RouterOutcome#FAILED}
     *         signals the caller to fall back to all tools
     */
    ToolRouterResult selectTools(
            String userPrompt,
            List<Message> recentHistory,
            List<McpTool> availableTools,
            int maxPrimaryTools
    );
}
