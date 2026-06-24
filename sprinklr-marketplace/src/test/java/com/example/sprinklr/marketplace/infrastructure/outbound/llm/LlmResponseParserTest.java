package com.example.sprinklr.marketplace.infrastructure.outbound.llm;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LlmResponseParserTest {

    private final LlmResponseParser parser = new LlmResponseParser();

    @Test
    void parse_includesUsageWhenPresent() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Hello"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 120,
                    "completion_tokens": 30,
                    "total_tokens": 150
                  }
                }
                """;

        LlmCompletionResult result = parser.parse(body);

        assertThat(result.content()).isEqualTo("Hello");
        assertThat(result.usage()).isNotNull();
        assertThat(result.usage().promptTokens()).isEqualTo(120);
        assertThat(result.usage().completionTokens()).isEqualTo(30);
        assertThat(result.usage().totalTokens()).isEqualTo(150);
    }

    @Test
    void parse_includesSpendingFromRouterResponse() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Hello"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 1382,
                    "completion_tokens": 96,
                    "total_tokens": 1478
                  },
                  "spending": 0.003532,
                  "additional": {
                    "spending": {
                      "total": 0.003532
                    }
                  }
                }
                """;

        LlmCompletionResult result = parser.parse(body);

        assertThat(result.usage()).isNotNull();
        assertThat(result.usage().promptTokens()).isEqualTo(1382);
        assertThat(result.usage().completionTokens()).isEqualTo(96);
        assertThat(result.usage().totalTokens()).isEqualTo(1478);
        assertThat(result.usage().spendingUsd()).isEqualByComparingTo(new BigDecimal("0.0035"));
    }

    @Test
    void parse_spendingFallsBackToAdditionalTotal() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Hello"
                    }
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15
                  },
                  "additional": {
                    "spending": {
                      "total": 0.0012
                    }
                  }
                }
                """;

        LlmCompletionResult result = parser.parse(body);

        assertThat(result.usage().spendingUsd()).isEqualByComparingTo(new BigDecimal("0.0012"));
    }

    @Test
    void parse_usageIsNullWhenOmitted() {
        String body = """
                {
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "Hello"
                    }
                  }]
                }
                """;

        LlmCompletionResult result = parser.parse(body);

        assertThat(result.usage()).isNull();
    }
}
