package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmToolUseRetryPolicyTest {

    @Test
    void detectsFalseDisconnectedResponse() {
        assertTrue(LlmToolUseRetryPolicy.falselyClaimsDisconnected(
                "I don't have access to your Jira data because the Jira integration is not currently connected."));
    }

    @Test
    void detectsLiveDataPromptWithPossessivePattern() {
        assertTrue(LlmToolUseRetryPolicy.requestsLiveIntegrationData("list my jira tickets (open)"));
    }

    @Test
    void skipsGeneralQuestionPrompt() {
        assertFalse(LlmToolUseRetryPolicy.requestsLiveIntegrationData("what is jira used for?"));
    }

    @Test
    void skipsDocCreationPromptWithoutLiveDataIntent() {
        assertFalse(LlmToolUseRetryPolicy.requestsLiveIntegrationData(
                "create a doc explaining jira workflows"));
    }

    @Test
    void shouldRetryWhenToolsAvailableButModelRefusedLiveData() {
        assertTrue(LlmToolUseRetryPolicy.shouldRetryForToolUse(
                40,
                true,
                "list my jira tickets",
                "Connect Jira from Profile → MCP Integrations.",
                false
        ));
    }

    @Test
    void shouldNotRetryWhenToolResultsAlreadyExistInTurn() {
        assertFalse(LlmToolUseRetryPolicy.shouldRetryForToolUse(
                122,
                true,
                "do i have any open merge requests",
                "You have no open merge requests.",
                true
        ));
    }

    @Test
    void retryReasonIsFalseDisconnectedWhenModelRefused() {
        assertEquals(
                LlmToolUseRetryPolicy.RetryReason.FALSE_DISCONNECTED,
                LlmToolUseRetryPolicy.retryReasonForToolUse(
                        40,
                        true,
                        "list my jira tickets",
                        "Connect Jira from Profile → MCP Integrations.",
                        false
                ).orElseThrow()
        );
    }

    @Test
    void shouldNotRetryWhenToolCallsReturned() {
        assertFalse(LlmToolUseRetryPolicy.shouldRetryForToolUse(
                40,
                false,
                "list my jira tickets",
                null,
                false
        ));
    }
}
