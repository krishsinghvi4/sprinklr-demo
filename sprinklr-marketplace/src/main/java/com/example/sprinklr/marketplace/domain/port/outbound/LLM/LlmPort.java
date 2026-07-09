package com.example.sprinklr.marketplace.domain.port.outbound.LLM;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmRequest;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmResponse;

import java.util.concurrent.Flow;

public interface LlmPort {

    /** Non-streaming: LLM decides text reply and/or tool calls. */

    LlmResponse complete(LlmRequest request);






    /** Streaming: LLM summarizes tool results into final user-facing text. */

    void streamSummary(LlmRequest request, Flow.Subscriber<String> subscriber);



}


