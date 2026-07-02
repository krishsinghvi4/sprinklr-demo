package com.example.sprinklr.marketplace.infrastructure.inbound.rest;

import com.example.sprinklr.marketplace.application.service.ChatConversationService;
import com.example.sprinklr.marketplace.domain.model.ChatRequest;
import com.example.sprinklr.marketplace.domain.model.Conversation;
import com.example.sprinklr.marketplace.domain.model.Message;
import com.example.sprinklr.marketplace.domain.model.MessageRole;
import com.example.sprinklr.marketplace.domain.port.inbound.ChatUseCase;
import com.example.sprinklr.marketplace.domain.port.outbound.ChatHistoryPort;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ChatApiRequest;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ChatHistoryResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ConversationListResponse;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto.ConversationSummaryDto;
import com.example.sprinklr.marketplace.infrastructure.outbound.llm.LlmErrorFormatter;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Flow;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 120_000L;
    private static final int PREVIEW_MAX_LENGTH = 80;

    private final ChatUseCase chatUseCase;
    private final ChatHistoryPort chatHistoryPort;
    private final ChatConversationService chatConversationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    public ChatController(
            ChatUseCase chatUseCase,
            ChatHistoryPort chatHistoryPort,
            ChatConversationService chatConversationService,
            AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.chatUseCase = chatUseCase;
        this.chatHistoryPort = chatHistoryPort;
        this.chatConversationService = chatConversationService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping(value = "/conversations", produces = MediaType.APPLICATION_JSON_VALUE)
    public ConversationListResponse listConversations() {
        String userId = authenticatedUserResolver.requireUserId();
        List<ConversationSummaryDto> conversations = chatHistoryPort.findConversationsByUserId(userId).stream()
                .map(conversation -> toSummaryDto(conversation))
                .toList();
        return new ConversationListResponse(conversations);
    }

    @GetMapping(value = "/history", produces = MediaType.APPLICATION_JSON_VALUE)
    public ChatHistoryResponse getChatHistory(
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "50") int limit) {
        String userId = authenticatedUserResolver.requireUserId();
        if (chatHistoryPort.findConversationByIdAndUserId(conversationId, userId).isEmpty()) {
            return ChatHistoryResponse.fromMessages(List.of());
        }

        List<Message> messages = chatHistoryPort.findRecentMessages(conversationId, limit);
        List<Message> displayMessages = messages.stream()
                .filter(message -> message.role() == MessageRole.USER || message.role() == MessageRole.ASSISTANT)
                .filter(message -> message.content() != null && !message.content().isBlank())
                .toList();
        return ChatHistoryResponse.fromMessages(displayMessages);
    }

    @DeleteMapping(value = "/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        String userId = authenticatedUserResolver.requireUserId();
        if (!chatConversationService.deleteConversation(conversationId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatApiRequest apiRequest) {
        String userId = authenticatedUserResolver.requireUserId();

        if (apiRequest.conversationId() != null && !apiRequest.conversationId().isBlank()) {
            boolean ownsConversation = chatHistoryPort
                    .findConversationByIdAndUserId(apiRequest.conversationId(), userId)
                    .isPresent();
            boolean conversationExists = chatHistoryPort
                    .findConversationById(apiRequest.conversationId())
                    .isPresent();

            if (conversationExists && !ownsConversation) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Conversation does not belong to this user");
            }
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        ChatRequest domainRequest = new ChatRequest(
                userId,
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
                try {
                    // Fallback: orchestrator normally delivers VPN/network errors as a chat message.
                    emitter.send(SseEmitter.event().data(LlmErrorFormatter.toUserMessage(throwable)));
                    emitter.complete();
                } catch (IOException exception) {
                    emitter.completeWithError(exception);
                }
            }

            @Override
            public void onComplete() {
                emitter.complete();
            }
        };

        chatUseCase.streamChat(domainRequest, subscriber);
        return emitter;
    }

    private ConversationSummaryDto toSummaryDto(Conversation conversation) {
        String preview = conversation.title();
        if (preview == null || preview.isBlank()) {
            preview = chatHistoryPort.findFirstUserMessageContent(conversation.id())
                    .map(this::truncatePreview)
                    .orElse("New conversation");
        }
        return new ConversationSummaryDto(
                conversation.id(),
                preview,
                conversation.createdAt(),
                conversation.updatedAt()
        );
    }

    private String truncatePreview(String text) {
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= PREVIEW_MAX_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, PREVIEW_MAX_LENGTH - 3) + "...";
    }
}
