package com.example.sprinklr.marketplace.application.service;

import com.example.sprinklr.marketplace.application.service.mcp.OAuthAuthFlowHandler;
import org.springframework.stereotype.Service;

/**
 * Facade for OAuth connect flows — delegates to {@link OAuthAuthFlowHandler}.
 */
@Service
public class McpOAuthService {

    private final OAuthAuthFlowHandler oauthAuthFlowHandler;

    public McpOAuthService(OAuthAuthFlowHandler oauthAuthFlowHandler) {
        this.oauthAuthFlowHandler = oauthAuthFlowHandler;
    }

    public String startOAuth(String userId, String catalogServerId) {
        return oauthAuthFlowHandler.startOAuth(userId, catalogServerId);
    }

    public void handleCallback(String state, String code) {
        oauthAuthFlowHandler.handleCallback(state, code);
    }
}
