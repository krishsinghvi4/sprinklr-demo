package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;

/**
 * Local development stub for {@link LlmPort} when {@code app.llm.stub-enabled=true}.
 * <p>
 * Simulates tool routing when the prompt mentions "jira" or "gitlab" so the MCP orchestration
 * path can be tested without a real router cookie. Conversation history is logged for multi-turn debugging.
 */
public class StubLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(StubLlmAdapter.class);

    @Override
    public LlmResponse complete(LlmRequest request) {
        int historySize = request.history().size();
        int conversationalCount = countConversationalMessages(request.history());
        log.info("[LLM-STUB] complete() historySize={} conversationalMessages={} promptPreview={}",
                historySize, conversationalCount, truncate(request.prompt(), 80));

        String promptLower = request.prompt().toLowerCase();
        if (promptLower.contains("jira") || promptLower.contains("gitlab")) {
            log.info("[LLM-STUB] Simulating tool_calls decision (jira/gitlab keyword detected)");
            return new LlmResponse(null, List.of(
                    new ToolCall(UUID.randomUUID().toString(), "jira.fetch_tickets", "{}"),
                    new ToolCall(UUID.randomUUID().toString(), "gitlab.fetch_pipelines", "{}")
            ));
        }

        String historyHint = conversationalCount > 1
                ? " (with " + conversationalCount + " messages of conversation context)"
                : "";
        log.info("[LLM-STUB] Simulating text-only response{}", historyHint);
        return new LlmResponse("This is a simulated AI response to: " + request.prompt() + historyHint, List.of());
    }

    @Override
    public void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber) {
        log.info("[LLM-STUB] streamSummary() historySize={}", request.history().size());

        String[] chunks = {
                "Based ", "on ", "the ", "tools, ", "your ", "Jira ", "and ", "GitLab ", "systems ", "are ", "green."
        };

        // Flow.Subscriber contract: onSubscribe must be called before onNext.
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                for (String chunk : chunks) {
                    subscriber.onNext(chunk);
                }
                subscriber.onComplete();
                log.info("[LLM-STUB] streamSummary complete");
            }

            @Override
            public void cancel() {
            }
        });
    }

    private static int countConversationalMessages(List<Message> history) {
        int count = 0;
        for (Message message : history) {
            if (message.role() == MessageRole.USER
                    || (message.role() == MessageRole.ASSISTANT && message.content() != null && !message.content().isBlank())) {
                count++;
            }
        }
        return count;
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...";
    }
}
