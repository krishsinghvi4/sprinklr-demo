package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ChatApiRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatUseCase chatUseCase;

    public ChatController(ChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody ChatApiRequest apiRequest) {
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
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                sink.tryEmitNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                sink.tryEmitError(throwable);
            }

            @Override
            public void onComplete() {
                sink.tryEmitComplete();
            }
        };

        // 4. Trigger the Domain logic (which will run on its own boundedElastic thread)
        chatUseCase.streamChat(domainRequest, subscriber);

        // 5. Return the Flux to Spring WebFlux for SSE streaming
        return sink.asFlux();
    }
}