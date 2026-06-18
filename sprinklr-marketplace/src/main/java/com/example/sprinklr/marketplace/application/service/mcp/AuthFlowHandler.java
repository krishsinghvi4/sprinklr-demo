package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.domain.model.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.McpConnectMethod;

/**
 * Handles a specific connect authentication flow (OAuth redirect or credential form).
 */
public interface AuthFlowHandler {

    McpConnectMethod connectMethod();

    boolean supports(McpCatalogEntry entry);
}
