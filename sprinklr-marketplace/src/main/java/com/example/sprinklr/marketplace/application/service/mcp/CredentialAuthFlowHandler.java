package com.example.sprinklr.marketplace.application.service.mcp;

import com.example.sprinklr.marketplace.application.service.McpMarketplaceService;
import com.example.sprinklr.marketplace.domain.model.MCP.McpCatalogEntry;
import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Credential-based connect flow (API token, basic auth, etc.).
 */
@Component
public class CredentialAuthFlowHandler implements AuthFlowHandler {

    private final McpConnectionOrchestrator connectionOrchestrator;
    private final McpProviderResolver providerResolver;
    private final TeamsWebhookTokenService teamsWebhookTokenService;
    private final String teamsEsUrl;
    private final String teamsEsApiKey;
    private final String teamsEsIndex;

    public CredentialAuthFlowHandler(
            McpConnectionOrchestrator connectionOrchestrator,
            McpProviderResolver providerResolver,
            TeamsWebhookTokenService teamsWebhookTokenService,
            @Value("${app.teams.es.url}") String teamsEsUrl,
            @Value("${app.teams.es.api-key}") String teamsEsApiKey,
            @Value("${app.teams.es.index}") String teamsEsIndex
    ) {
        this.connectionOrchestrator = connectionOrchestrator;
        this.providerResolver = providerResolver;
        this.teamsWebhookTokenService = teamsWebhookTokenService;
        this.teamsEsUrl = teamsEsUrl;
        this.teamsEsApiKey = teamsEsApiKey;
        this.teamsEsIndex = teamsEsIndex;
    }

    @Override
    public McpConnectMethod connectMethod() {
        return McpConnectMethod.CREDENTIAL_FORM;
    }

    @Override
    public boolean supports(McpCatalogEntry entry) {
        return entry.connectMethod() == McpConnectMethod.CREDENTIAL_FORM
                || entry.connectMethod() == McpConnectMethod.LOCAL_ONLY;
    }

    public McpMarketplaceService.ConnectionView connect(
            String userId,
            McpCatalogEntry entry,
            Map<String, String> credentials
    ) {
        if (entry.connectMethod() != McpConnectMethod.CREDENTIAL_FORM
                && entry.connectMethod() != McpConnectMethod.LOCAL_ONLY) {
            throw new IllegalArgumentException(
                    "OAuth connection required for " + entry.displayName()
                            + ". Use the OAuth connect flow instead.");
        }
        Map<String, String> resolvedCredentials = enrichCredentials(userId, entry, credentials);
        McpProvider provider = providerResolver.resolve(entry);
        provider.validateCredentials(entry, resolvedCredentials);
        return connectionOrchestrator.connectWithCredentials(userId, entry.id(), resolvedCredentials);
    }

    private Map<String, String> enrichCredentials(
            String userId,
            McpCatalogEntry entry,
            Map<String, String> credentials
    ) {
        if (!"teams-messages".equals(entry.id())) {
            return credentials;
        }
        Map<String, String> enriched = new HashMap<>(credentials);
        String token = teamsWebhookTokenService.generateToken(userId);
        enriched.put("webhookToken", token);
        enriched.put("webhookUrl", teamsWebhookTokenService.buildWebhookUrl(token));
        enriched.put("esUrl", teamsEsUrl);
        enriched.put("esApiKey", teamsEsApiKey);
        enriched.put("esIndex", teamsEsIndex == null || teamsEsIndex.isBlank() ? "teams_messages" : teamsEsIndex);
        return enriched;
    }
}
