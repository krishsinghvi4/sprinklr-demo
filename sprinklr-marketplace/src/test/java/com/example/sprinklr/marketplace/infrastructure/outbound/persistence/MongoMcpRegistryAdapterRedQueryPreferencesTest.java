package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.domain.model.MCP.McpConnectionStatus;
import com.example.sprinklr.marketplace.domain.model.MCP.McpUserConnection;
import com.example.sprinklr.marketplace.domain.model.RedQueryPreferences;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local.McpLocalToolCatalogMerger;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters.MongoMcpRegistryAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.MongoServerTypeConfigDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.RedQueryPreferencesDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.McpConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoMcpRegistryAdapterRedQueryPreferencesTest {

    @Mock
    private McpConnectionRepository repository;

    @Mock
    private McpLocalToolCatalogMerger localToolCatalogMerger;

    @InjectMocks
    private MongoMcpRegistryAdapter adapter;

    @Test
    void updateRedQueryPreferencesPersistsDocument() {
        McpConnectionDocument existing = redConnection(null);
        when(repository.findByIdAndUserId("conn-red", "user-1")).thenReturn(Optional.of(existing));

        RedQueryPreferences preferences = new RedQueryPreferences(
                List.of("AUDIT_LOGS"),
                List.of(new RedQueryPreferences.MongoServerTypeConfig("PAID", List.of("adSet"))));

        adapter.updateRedQueryPreferences("user-1", "conn-red", preferences);

        ArgumentCaptor<McpConnectionDocument> captor = ArgumentCaptor.forClass(McpConnectionDocument.class);
        verify(repository).save(captor.capture());
        RedQueryPreferencesDocument saved = captor.getValue().redQueryPreferences();
        assertEquals(List.of("AUDIT_LOGS"), saved.elasticsearchServerTypes());
        assertEquals("PAID", saved.mongoServerTypes().get(0).serverType());
        assertEquals(List.of("adSet"), saved.mongoServerTypes().get(0).collectionNames());
    }

    @Test
    void findRedQueryPreferencesReturnsConfiguredValues() {
        RedQueryPreferencesDocument document = new RedQueryPreferencesDocument(
                List.of("AUDIENCE_CONTAINER"),
                List.of(new MongoServerTypeConfigDocument("DEFAULT", List.of("audience"))));
        when(repository.findByIdAndUserId("conn-red", "user-1"))
                .thenReturn(Optional.of(redConnection(document)));

        Optional<RedQueryPreferences> preferences = adapter.findRedQueryPreferences("user-1", "conn-red");

        assertTrue(preferences.isPresent());
        assertEquals(List.of("AUDIENCE_CONTAINER"), preferences.get().elasticsearchServerTypes());
        assertEquals("DEFAULT", preferences.get().mongoServerTypes().get(0).serverType());
    }

    @Test
    void saveConnectionPreservesExistingRedQueryPreferences() {
        RedQueryPreferencesDocument existingPrefs = new RedQueryPreferencesDocument(
                List.of("AUDIT_LOGS"), List.of());
        McpConnectionDocument existing = redConnection(existingPrefs);
        when(repository.findById("conn-red")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        adapter.saveConnection(
                new McpUserConnection(
                        "conn-red",
                        "user-1",
                        "red-mcp",
                        McpConnectionStatus.CONNECTED,
                        "session",
                        "2025-03-26",
                        List.of(),
                        Instant.now(),
                        null),
                null,
                "red");

        ArgumentCaptor<McpConnectionDocument> captor = ArgumentCaptor.forClass(McpConnectionDocument.class);
        verify(repository).save(captor.capture());
        assertEquals(existingPrefs, captor.getValue().redQueryPreferences());
    }

    private static McpConnectionDocument redConnection(RedQueryPreferencesDocument preferences) {
        return new McpConnectionDocument(
                "conn-red",
                "user-1",
                "red-mcp",
                "red",
                "enc",
                "session-1",
                "2025-03-26",
                "CONNECTED",
                List.of(),
                Instant.now(),
                null,
                null,
                null,
                preferences
        );
    }
}
