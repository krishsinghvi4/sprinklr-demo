package com.example.sprinklr.marketplace.domain.port.inbound;

import com.example.sprinklr.marketplace.domain.model.ChatRequest;

import java.util.concurrent.Flow;

public interface ChatUseCase {

    /**
     * Processes a user prompt for a conversation, streaming assistant
     * response chunks to the subscriber. Completes or signals error via Flow.
     */
    void streamChat(ChatRequest request, Flow.Subscriber<String> responseSubscriber);
}
