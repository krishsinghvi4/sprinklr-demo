package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.auth;

import java.util.Map;

public interface McpAuthStrategy {

    String authType();

    Map<String, String> buildAuthHeaders(Map<String, String> credentials);
}
