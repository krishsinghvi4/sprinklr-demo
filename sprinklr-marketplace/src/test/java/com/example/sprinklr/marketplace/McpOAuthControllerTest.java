package com.example.sprinklr.marketplace;

import com.example.sprinklr.marketplace.application.service.McpOAuthService;
import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.inbound.rest.controllers.McpOAuthController;
import com.example.sprinklr.marketplace.infrastructure.security.AuthenticatedUserResolver;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class McpOAuthControllerTest {

    @Test
    void callbackMissingStateRedirectsToError() {
        McpOAuthService oauthService = mock(McpOAuthService.class);
        AuthenticatedUserResolver resolver = mock(AuthenticatedUserResolver.class);
        McpProperties properties = new McpProperties();
        properties.setOauthErrorRedirectUrl("http://localhost/error");

        McpOAuthController controller = new McpOAuthController(oauthService, resolver, properties);
        var response = controller.callback("code-123", null, null);

        assertEquals(302, response.getStatusCode().value());
        assertNotNull(response.getHeaders().getLocation());
        assertEquals(
                "OAuth callback was incomplete. Please try again.",
                parseQueryParam(response.getHeaders().getLocation(), "message")
        );
        verifyNoInteractions(oauthService);
    }

    private static String parseQueryParam(URI uri, String name) {
        String query = uri.getQuery();
        if (query == null) {
            return null;
        }
        for (String part : query.split("&")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals(name)) {
                return URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
