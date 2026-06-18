package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

/**
 * Heuristics for retrying an LLM completion when tools are available but the model skips them.
 */
final class LlmToolUseRetryPolicy {

    private LlmToolUseRetryPolicy() {
    }

    static boolean shouldRetryForToolUse(
            int toolCount,
            boolean textOnlyResponse,
            String prompt,
            String responseContent
    ) {
        if (toolCount <= 0 || !textOnlyResponse) {
            return false;
        }
        if (falselyClaimsDisconnected(responseContent)) {
            return true;
        }
        return requestsLiveIntegrationData(prompt);
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
        boolean mentionsIntegration = lower.contains("jira")
                || lower.contains("gitlab")
                || lower.contains("merge request")
                || lower.contains("pipeline")
                || lower.contains("deployment")
                || lower.contains("teams");
        boolean requestsData = lower.contains("list")
                || lower.contains("fetch")
                || lower.contains("get my")
                || lower.contains("show my")
                || lower.contains("assigned")
                || lower.contains("open ticket")
                || lower.contains("create")
                || lower.contains("update")
                || lower.contains("search");
        return mentionsIntegration && requestsData;
    }
}
