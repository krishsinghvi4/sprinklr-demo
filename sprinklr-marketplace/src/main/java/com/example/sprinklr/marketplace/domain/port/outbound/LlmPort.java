package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LlmResponse;

import java.util.concurrent.Flow;

public interface LlmPort {

    /** Non-streaming: LLM decides text reply and/or tool calls. */
    //blockign call
    LlmResponse complete(LlmRequest request);
    /*
    What it does:
When the orchestrator calls this function, it passes the user's prompt and a list of available McpTool JSON schemas. This function executes a standard HTTP POST to the LLM API and blocks the thread until the full response is returned.

Why it MUST be non-streaming:
In this specific step, the LLM is deciding whether to execute a tool. If it chooses a tool, it outputs a strict JSON structure containing the tool name and arguments.
Your Java backend uses a JSON deserializer (like Jackson) to convert that text into a ToolCall record. You cannot deserialize an incomplete JSON string. If you used streaming here, your code would crash trying to parse {"tool": "ji before the rest of the string arrived. Therefore, the function must wait for the entire response to ensure structural integrity before returning the LlmResponse object.
    */





    /** Streaming: LLM summarizes tool results into final user-facing text. */
    //non blocking call
    void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber);

    /*
    Role in the Architecture: The Final Synthesis Output.

What it does:
After your application executes the tools (e.g., fetching Jira data), it appends the raw JSON results to the LlmRequest history and calls this function. This function establishes an HTTP connection with the LLM provider requesting a text stream (Accept: text/event-stream).

Why it MUST be streaming:
At this phase, the LLM is converting the raw Jira JSON into human-readable text. Because this text is destined directly for the user's screen, waiting for the LLM to write a 300-word summary before displaying anything would result in unacceptable UI latency.

Instead, as the external HTTP client receives each text chunk from the LLM provider over the network, this function immediately invokes subscriber.onNext(chunk). This triggers the reactive pipeline, pushing the token out of your ChatController and onto the user's screen instantly. This function has a void return type because the data is pushed out via the subscriber callback, not returned to the calling method
    */

}


