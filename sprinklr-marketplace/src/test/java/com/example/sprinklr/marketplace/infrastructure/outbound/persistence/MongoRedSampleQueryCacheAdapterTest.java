package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.infrastructure.config.MCP.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKeyBuilder;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.adapters.MongoRedSampleQueryCacheAdapter;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.RedSampleQueryCacheDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.RedSampleQueryCacheRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoRedSampleQueryCacheAdapterTest {

    private static final String SAMPLE_WITH_DOCS =
            "{\"hits\":{\"hits\":[{\"_source\":{\"entityType\":\"PAID_INITIATIVE\",\"adDeliveryType\":\"STANDARD\"}}]}}";

    private static final String SAMPLE_HIT_WITHOUT_SOURCE =
            "{\"hits\":{\"hits\":[{\"_id\":\"x\",\"_index\":\"idx\"}]}}";

    @Mock
    private RedSampleQueryCacheRepository repository;

    private MongoRedSampleQueryCacheAdapter adapter;
    private RedSampleQueryCacheKeyBuilder keyBuilder;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        keyBuilder = new RedSampleQueryCacheKeyBuilder(properties);
        adapter = new MongoRedSampleQueryCacheAdapter(
                repository,
                keyBuilder,
                properties
        );
    }

    @Test
    void findReturnsFreshCachedContent() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        var key = keyBuilder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);

        when(repository.findById(key.id())).thenReturn(Optional.of(new RedSampleQueryCacheDocument(
                key.id(),
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                key.scopeArgsJson(),
                SAMPLE_WITH_DOCS,
                Instant.now().plusSeconds(3600)
        )));

        Optional<String> found = adapter.find(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args
        );

        assertTrue(found.isPresent());
        assertEquals(SAMPLE_WITH_DOCS, found.get());
    }

    @Test
    void findDeletesStaleEntryWithoutFilterablePaths() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        var key = keyBuilder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);

        when(repository.findById(key.id())).thenReturn(Optional.of(new RedSampleQueryCacheDocument(
                key.id(),
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                key.scopeArgsJson(),
                SAMPLE_HIT_WITHOUT_SOURCE,
                Instant.now().plusSeconds(3600)
        )));

        assertTrue(adapter.find(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args
        ).isEmpty());

        verify(repository).deleteById(key.id());
    }

    @Test
    void findSkipsZeroHitCachedEntry() {
        String args = """
                {"partnerId":190,"serverType":"PAID","queryType":"general","collectionName":"paidInitiative","env":"prod"}
                """;
        var key = keyBuilder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL, args);

        when(repository.findById(key.id())).thenReturn(Optional.of(new RedSampleQueryCacheDocument(
                key.id(),
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                key.scopeArgsJson(),
                "[]",
                Instant.now().plusSeconds(3600)
        )));

        assertTrue(adapter.find(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                args
        ).isEmpty());
        verify(repository).deleteById(key.id());
    }

    @Test
    void findSkipsIncompleteMongoScope() {
        String args = """
                {"partnerId":190,"serverType":"PAID","queryType":"general","env":"prod"}
                """;

        assertTrue(adapter.find(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                args
        ).isEmpty());
        verify(repository, never()).findById(anyString());
    }

    @Test
    void saveSkipsZeroHitResponses() {
        String args = """
                {"partnerId":190,"serverType":"PAID","queryType":"general","collectionName":"paidInitiative","env":"prod"}
                """;

        adapter.save(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                args,
                "[]"
        );

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveSkipsHitWithoutFilterablePaths() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;

        adapter.save(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args,
                SAMPLE_HIT_WITHOUT_SOURCE
        );

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveSkipsIncompleteMongoScope() {
        String args = """
                {"partnerId":190,"serverType":"PAID","queryType":"general","env":"prod"}
                """;

        adapter.save(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.MONGO_SAMPLE_TOOL,
                args,
                SAMPLE_WITH_DOCS
        );

        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void saveStoresFullResultWithTtl() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        String longBody = SAMPLE_WITH_DOCS + "x".repeat(12_000);

        adapter.save(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args,
                longBody
        );

        ArgumentCaptor<RedSampleQueryCacheDocument> captor =
                ArgumentCaptor.forClass(RedSampleQueryCacheDocument.class);
        verify(repository).save(captor.capture());
        RedSampleQueryCacheDocument saved = captor.getValue();
        assertEquals(longBody, saved.resultContent());
        assertTrue(saved.resultContent().length() > 12_000);
        assertTrue(saved.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void deleteRemovesExistingEntry() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        var key = keyBuilder.build("user-1", "conn-1", RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL, args);
        when(repository.existsById(key.id())).thenReturn(true);

        adapter.delete(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args
        );

        verify(repository).deleteById(key.id());
    }
}
