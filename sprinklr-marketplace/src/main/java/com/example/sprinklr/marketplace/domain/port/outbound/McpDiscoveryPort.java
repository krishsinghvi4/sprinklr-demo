package com.example.sprinklr.marketplace.domain.port.outbound;

import com.example.sprinklr.marketplace.domain.model.McpTool;

import java.util.List;
import java.util.Map;

public interface McpDiscoveryPort {

    /**
     * Runs MCP initialize + tools/list against the given endpoint.
     *
     * @return discovered tools and the session id assigned by the server (may be null)
     */
    McpDiscoveryResult discover(String endpointUrl, Map<String, String> authHeaders, String serverIdPrefix, String connectionId);

    record McpDiscoveryResult(String sessionId, String protocolVersion, List<McpTool> tools) {}
}
