package com.example.sprinklr.marketplace.infrastructure.inbound.rest.dto;

public record ChatApiRequest(
        String userId,
        String conversationId,
        String prompt
) {}

/*
Look at what the React frontend actually needs to send over the network versus what the AI model actually needs to receive.

What the Frontend sends (ChatApiRequest):
The client only knows three things: Who they are (userId), what chat window they are in (conversationId), and what they just typed (prompt). It is a tiny, 100-byte JSON payload.

What the AI needs (LlmRequest):
The OpenAI/Anthropic API has no memory. To answer the prompt, it needs the entire chat history of the conversation and the massive JSON schemas of every tool it is allowed to use.

If you forced the React frontend to use LlmRequest, the client browser would have to download the entire conversation history and all the tool schemas, and then send them back to the server on every single keystroke. This would cause massive network latency.
*/

/*
2. The Trust Boundary (Security)
Your Spring Boot Controller is the border wall of your application. You can never trust the data coming from the client browser.

If your @PostMapping accepted an LlmRequest object directly, a malicious user could open Postman, construct a fake LlmRequest, and inject fake conversation history or alter the system prompts before it reaches your AI.

By using ChatApiRequest, you strictly limit the attack surface. You tell the frontend: "You are only allowed to give me your new prompt. I will securely fetch the historical truth from MongoDB myself, and I will construct the LlmRequest internally."
*/