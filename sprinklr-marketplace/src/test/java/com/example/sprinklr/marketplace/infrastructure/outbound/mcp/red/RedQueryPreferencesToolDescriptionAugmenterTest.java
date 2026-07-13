package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.domain.model.MCP.McpTool;
import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.domain.model.tool.ToolDependencyGraph;
import com.example.sprinklr.marketplace.domain.port.outbound.MCP.McpRegistryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedQueryPreferencesToolDescriptionAugmenterTest {

    private static final String CONNECTION_ID = "conn-red";
    private static final String USER_ID = "user-1";

    private StubMcpRegistryPort registryPort;
    private RedQueryPreferencesToolDescriptionAugmenter augmenter;

    private final RedQueryPreferences preferences = new RedQueryPreferences(
            List.of("AUDIENCE_CONTAINER", "AUDIT_LOGS"),
            List.of(
                    new RedQueryPreferences.MongoServerTypeConfig("PAID", List.of("paidInitiative", "adSet")),
                    new RedQueryPreferences.MongoServerTypeConfig("DEFAULT", List.of("audience", "campaign"))
            )
    );

    @BeforeEach
    void setUp() {
        registryPort = new StubMcpRegistryPort();
        registryPort.preferencesByConnection.put(CONNECTION_ID, preferences);
        augmenter = new RedQueryPreferencesToolDescriptionAugmenter(registryPort);
    }

    @Test
    void prependsElasticsearchAllowlistToEsQueryTools() {
        McpTool tool = redTool("red.red_execute_elastic_search_query", "Execute ES query");

        List<McpTool> augmented = augmenter.augment(USER_ID, List.of(tool));

        assertEquals(1, augmented.size());
        assertTrue(augmented.get(0).description().startsWith(
                "User allowlist (Elasticsearch-only serverTypes): AUDIENCE_CONTAINER, AUDIT_LOGS."));
        assertTrue(augmented.get(0).description().endsWith("Execute ES query"));
        assertEquals(1, registryPort.lookupCount);
    }

    @Test
    void prependsMongoAllowlistToMongoQueryTools() {
        McpTool tool = redTool("red.red_sample_mongo_query", "Sample mongo query");

        List<McpTool> augmented = augmenter.augment(USER_ID, List.of(tool));

        assertEquals(1, augmented.size());
        assertTrue(augmented.get(0).description().startsWith(
                "User allowlist (Mongo-only): PAID/paidInitiative,adSet; DEFAULT/audience,campaign."));
        assertTrue(augmented.get(0).description().endsWith("Sample mongo query"));
    }

    @Test
    void leavesNonQueryRedToolsUnchanged() {
        McpTool tool = redTool("red.red_ping", "Ping RED");

        List<McpTool> augmented = augmenter.augment(USER_ID, List.of(tool));

        assertEquals("Ping RED", augmented.get(0).description());
        assertEquals(0, registryPort.lookupCount);
    }

    @Test
    void leavesToolsUnchangedWhenPreferencesEmpty() {
        registryPort.preferencesByConnection.put(CONNECTION_ID, new RedQueryPreferences(List.of(), List.of()));
        McpTool tool = redTool("red.red_execute_mongo_query", "Execute mongo query");

        List<McpTool> augmented = augmenter.augment(USER_ID, List.of(tool));

        assertEquals("Execute mongo query", augmented.get(0).description());
    }

    @Test
    void leavesNonRedToolsUnchanged() {
        McpTool tool = new McpTool("jira.getJiraIssue", "Get issue", "conn-jira", "{}");

        List<McpTool> augmented = augmenter.augment(USER_ID, List.of(tool));

        assertEquals("Get issue", augmented.get(0).description());
        assertEquals(0, registryPort.lookupCount);
    }

    @Test
    void loadsPreferencesOncePerConnection() {
        McpTool esTool = redTool("red.red_sample_elasticsearch_query", "Sample ES");
        McpTool mongoTool = redTool("red.red_execute_mongo_query", "Execute mongo");

        augmenter.augment(USER_ID, List.of(esTool, mongoTool));

        assertEquals(1, registryPort.lookupCount);
    }

    @Test
    void returnsEmptyListWhenToolsNull() {
        assertTrue(augmenter.augment(USER_ID, null).isEmpty());
    }

    @Test
    void returnsOriginalToolsWhenUserIdBlank() {
        McpTool tool = redTool("red.red_execute_mongo_query", "Execute mongo query");
        List<McpTool> tools = List.of(tool);

        assertEquals(tools, augmenter.augment(" ", tools));
        assertEquals(0, registryPort.lookupCount);
    }

    private static McpTool redTool(String name, String description) {
        return new McpTool(name, description, CONNECTION_ID, "{\"type\":\"object\"}");
    }

    private static final class StubMcpRegistryPort implements McpRegistryPort {

        private final Map<String, RedQueryPreferences> preferencesByConnection = new HashMap<>();
        private int lookupCount;

        @Override
        public Optional<RedQueryPreferences> findRedQueryPreferences(String userId, String connectionId) {
            lookupCount++;
            return Optional.ofNullable(preferencesByConnection.get(connectionId));
        }

        @Override
        public McpUserConnection saveConnection(
                McpUserConnection connection,
                String encryptedCredentials,
                String serverIdPrefix
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<McpUserConnection> findByUserId(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<McpUserConnection> findByIdAndUserId(String connectionId, String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<McpUserConnection> findByUserIdAndCatalogServerId(String userId, String catalogServerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<McpUserConnection> findByUserIdAndServerIdPrefix(String userId, String serverIdPrefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateSession(String connectionId, String sessionId, String protocolVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearSession(String connectionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String connectionId, String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<McpTool> findActiveToolsForUser(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateDependencyGraph(String connectionId, ToolDependencyGraph graph) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ToolDependencyGraph> findActiveDependencyGraphsForUser(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ToolDependencyGraph> findDependencyGraphByConnectionId(String connectionId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRedQueryPreferences(
                String userId,
                String connectionId,
                RedQueryPreferences preferences
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<String> findEncryptedCredentials(String userId, String connectionId) {
            throw new UnsupportedOperationException();
        }
    }
}
