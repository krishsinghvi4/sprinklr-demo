package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final ChatUseCase chatUseCase;
    private final ChatHistoryPort chatHistoryPort;

    public ChatController(ChatUseCase chatUseCase, ChatHistoryPort chatHistoryPort) {
        this.chatUseCase = chatUseCase;
        this.chatHistoryPort = chatHistoryPort;
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatHistoryResponse getChatHistory(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        List<Message> messages = chatHistoryPort.findRecentMessages(conversationId, limit);
        List<Message> displayMessages = messages.stream()
                .filter(message -> message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .toList();
        return ChatHistoryResponse.fromMessages(displayMessages);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatApiRequest apiRequest) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        ChatRequest domainRequest = new ChatRequest(
                apiRequest.userId(),
                apiRequest.conversationId(),
                apiRequest.prompt()
        );

        Flow.Subscriber<String> subscriber = new Flow.Subscriber<>() {
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                try {
                    emitter.send(SseEmitter.event().data(item));
                } catch (IOException exception) {
                    subscription.cancel();
                    emitter.completeWithError(exception);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                emitter.completeWithError(throwable);
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }
        };

        chatUseCase.streamChat(domainRequest, subscriber);
        return emitter;
    }
}
