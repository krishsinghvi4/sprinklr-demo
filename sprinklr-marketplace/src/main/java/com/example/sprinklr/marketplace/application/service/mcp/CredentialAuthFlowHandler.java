package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Credential-based connect flow (API token, basic auth, etc.).
 */
@Component
public class CredentialAuthFlowHandler implements AuthFlowHandler {

    private final McpConnectionOrchestrator connectionOrchestrator;
    private final McpProviderResolver providerResolver;

    public CredentialAuthFlowHandler(
            McpConnectionOrchestrator connectionOrchestrator,
            McpProviderResolver providerResolver
    ) {
        this.connectionOrchestrator = connectionOrchestrator;
        this.providerResolver = providerResolver;
    }

    @Override
    public McpConnectMethod connectMethod() {
        return McpConnectMethod.CREDENTIAL_FORM;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return entry.connectMethod() == McpConnectMethod.CREDENTIAL_FORM;
    }

    public McpMarketplaceService.ConnectionView connect(
            String userId,
            McpCatalogEntry entry,
            Map<String, String> credentials
    ) {
        if (entry.connectMethod() != McpConnectMethod.CREDENTIAL_FORM) {
            throw new IllegalArgumentException(
                    "OAuth connection required for " + entry.displayName()
                            + ". Use the OAuth connect flow instead.");
        }
        McpProvider provider = providerResolver.resolve(entry);
        provider.validateCredentials(entry, credentials);
        return connectionOrchestrator.connectWithCredentials(userId, entry.id(), credentials);
    }
}
