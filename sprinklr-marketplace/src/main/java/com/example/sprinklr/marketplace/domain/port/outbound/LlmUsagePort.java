package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.LlmUsageEvent;
import com.example.sprinklr.marketplace.domain.model.LlmUsageSummary;

public interface LlmUsagePort {

    void record(LlmUsageEvent event);

    LlmUsageSummary getSummaryForUser(String userId);
}
