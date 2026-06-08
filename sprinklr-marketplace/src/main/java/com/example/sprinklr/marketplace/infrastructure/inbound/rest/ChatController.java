package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ChatApiRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ChatHistoryResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatUseCase chatUseCase;
    private final ChatHistoryPort chatHistoryPort;

    public ChatController(ChatUseCase chatUseCase, ChatHistoryPort chatHistoryPort) {
        this.chatUseCase = chatUseCase;
        this.chatHistoryPort = chatHistoryPort;
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatHistoryResponse> getChatHistory(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        return Mono.fromCallable(() -> {
            System.out.println("[WebFlux] getChatHistory called for conversation: " + conversationId);
            List<Message> messages = chatHistoryPort.findRecentMessages(conversationId, limit);
            System.out.println("[WebFlux] Retrieved " + messages.size() + " messages");
            return ChatHistoryResponse.fromMessages(messages);
        }).subscribeOn(Schedulers.boundedElastic())
         .onErrorResume(error -> {
             System.err.println("[WebFlux] Error fetching history: " + error.getMessage());
             return Mono.just(ChatHistoryResponse.fromMessages(List.of()));
         });
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatApiRequest apiRequest) {
        System.out.println("[Controller] streamChat called for conversation: " + apiRequest.conversationId());
        
        // 1. Map the web JSON to the pure Domain Request
        ChatRequest domainRequest = new ChatRequest(
                apiRequest.userId(),
                apiRequest.conversationId(),
                apiRequest.prompt()
        );

        // 2. Create a WebFlux Sink to act as the bridge
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 3. Create a standard Java Flow.Subscriber that feeds the WebFlux Sink
        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;
            
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                System.out.println("[Controller] Emitting chunk: " + item.substring(0, Math.min(50, item.length())));
                sink.tryEmitNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                System.err.println("[Controller] Error in subscriber: " + throwable.getMessage());
                sink.tryEmitError(throwable);
            }

            @Override
            public void onComplete() {
                System.out.println("[Controller] Subscriber completed");
                sink.tryEmitComplete();
            }
        };

        // 4. Trigger the Domain logic (which will run on its own boundedElastic thread)
        System.out.println("[Controller] Starting chatUseCase.streamChat");
        chatUseCase.streamChat(domainRequest, subscriber);

        // 5. Return the Flux to Spring WebFlux for SSE streaming
        return sink.asFlux();
    }
}