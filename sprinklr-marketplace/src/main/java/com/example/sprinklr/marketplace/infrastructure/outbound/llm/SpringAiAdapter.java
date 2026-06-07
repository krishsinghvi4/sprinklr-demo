package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;
import com.example.sprinklr.marketplace.domain.model.ToolCall;
import com.example.sprinklr.marketplace.domain.port.outbound.LlmPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Flow;

@Component
public class SpringAiAdapter implements LlmPort {

    @Override
    public LlmResponse complete(LlmRequest request) {
        System.out.println("--> [LLM] Analyzing prompt: " + request.prompt());
        
        // Simulating the LLM deciding it needs to use tools
        if (request.prompt().toLowerCase().contains("jira") || request.prompt().toLowerCase().contains("gitlab")) {
            System.out.println("<-- [LLM] Decision: Tools required!");
            return new LlmResponse(null, List.of(
                    new ToolCall(UUID.randomUUID().toString(), "jira.fetch_tickets", "{}"),
                    new ToolCall(UUID.randomUUID().toString(), "gitlab.fetch_pipelines", "{}")
            ));
        }

        // Standard text response
        System.out.println("<-- [LLM] Decision: Standard text response.");
        return new LlmResponse("This is a simulated AI response to: " + request.prompt(), List.of());
    }

    @Override
    public void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber) {
        System.out.println("--> [LLM] Generating final summary from tool data...");
        
        String[] chunks = {"Based ", "on ", "the ", "tools, ", "your ", "Jira ", "and ", "GitLab ", "systems ", "are ", "green."};
        
        new Thread(() -> {
            for (String chunk : chunks) {
                try {
                    Thread.sleep(200); // Simulate token-by-token streaming
                    subscriber.onNext(chunk);
                } catch (InterruptedException e) {
                    subscriber.onError(e);
                }
            }
            subscriber.onComplete();
            System.out.println("<-- [LLM] Stream complete.");
        }).start();
    }
}