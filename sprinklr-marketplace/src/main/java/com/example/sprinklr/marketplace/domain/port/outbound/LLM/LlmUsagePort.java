package com.example.sprinklr.marketplace.domain.port.outbound.LLM;

import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageEvent;
import com.example.sprinklr.marketplace.domain.model.LLM.LlmUsageSummary;

public interface LlmUsagePort {

    void record(LlmUsageEvent event);

    LlmUsageSummary getSummaryForUser(String userId);
}
