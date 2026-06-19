package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import java.util.Optional;

/**
 * Heuristics for retrying an LLM completion when tools are available but the model skips them.
 */
final class LlmToolUseRetryPolicy {

    enum RetryReason {
        FALSE_DISCONNECTED,
        LIVE_DATA_PROMPT
    }

    private LlmToolUseRetryPolicy() {
    }

    static Optional<RetryReason> retryReasonForToolUse(
            int toolCount,
            boolean textOnlyResponse,
            String prompt,
            String responseContent,
            boolean toolResultsAlreadyInTurn
    ) {
        if (toolCount <= 0 || !textOnlyResponse || toolResultsAlreadyInTurn) {
            return Optional.empty();
        }
        if (falselyClaimsDisconnected(responseContent)) {
            return Optional.of(RetryReason.FALSE_DISCONNECTED);
        }
        if (requestsLiveIntegrationData(prompt)) {
            return Optional.of(RetryReason.LIVE_DATA_PROMPT);
        }
        return Optional.empty();
    }

    static boolean shouldRetryForToolUse(
            int toolCount,
            boolean textOnlyResponse,
            String prompt,
            String responseContent,
            boolean toolResultsAlreadyInTurn
    ) {
        return retryReasonForToolUse(
                toolCount, textOnlyResponse, prompt, responseContent, toolResultsAlreadyInTurn
        ).isPresent();
    }

    static boolean falselyClaimsDisconnected(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase();
        return lower.contains("not connected")
                || lower.contains("not currently connected")
                || lower.contains("do not have access")
                || lower.contains("don't have access")
                || lower.contains("do not have direct access")
                || lower.contains("don't have direct access")
                || lower.contains("cannot fetch live")
                || lower.contains("can't fetch live")
                || lower.contains("without this tool enabled")
                || lower.contains("enable the jira")
                || lower.contains("enable the gitlab")
                || lower.contains("mcp integrations")
                || lower.contains("connect via profile")
                || lower.contains("profile → mcp");
    }

    static boolean requestsLiveIntegrationData(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        String lower = prompt.toLowerCase();
        if (isGeneralKnowledgeQuestion(lower)) {
            return false;
        }
        boolean mentionsIntegration = lower.contains("jira")
                || lower.contains("gitlab")
                || lower.contains("merge request")
                || lower.contains("pipeline")
                || lower.contains("deployment")
                || lower.contains("teams");
        boolean requestsLiveData = requestsPossessiveLiveData(lower)
                || requestsExplicitFetch(lower);
        return mentionsIntegration && requestsLiveData;
    }

    private static boolean isGeneralKnowledgeQuestion(String lower) {
        return lower.startsWith("what is ")
                || lower.startsWith("what are ")
                || lower.startsWith("how does ")
                || lower.startsWith("explain ")
                || lower.contains(" used for")
                || lower.contains("tell me about ");
    }

    private static boolean requestsPossessiveLiveData(String lower) {
        return lower.contains("my jira")
                || lower.contains("my gitlab")
                || lower.contains("my ticket")
                || lower.contains("my tickets")
                || lower.contains("my merge request")
                || lower.contains("my pipeline")
                || lower.contains("my deployment")
                || lower.contains("assigned to me")
                || lower.contains("get my")
                || lower.contains("show my")
                || lower.contains("list my");
    }

    private static boolean requestsExplicitFetch(String lower) {
        return (lower.contains("fetch") || lower.contains("list") || lower.contains("search"))
                && (lower.contains("ticket") || lower.contains("issue") || lower.contains("merge request")
                || lower.contains("pipeline") || lower.contains("deployment"));
    }
}
