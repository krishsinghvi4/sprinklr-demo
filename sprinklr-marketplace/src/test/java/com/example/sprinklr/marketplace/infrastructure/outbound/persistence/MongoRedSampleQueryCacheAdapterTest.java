package com.example.sprinklr.marketplace.infrastructure.outbound.persistence;

import com.example.sprinklr.marketplace.infrastructure.config.McpProperties;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red.RedSampleQueryCacheKeyBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoRedSampleQueryCacheAdapterTest {

    @Mock
    private RedSampleQueryCacheRepository repository;

    private MongoRedSampleQueryCacheAdapter adapter;

    @BeforeEach
    void setUp() {
        McpProperties properties = new McpProperties();
        adapter = new MongoRedSampleQueryCacheAdapter(
                repository,
                new RedSampleQueryCacheKeyBuilder(),
                properties
        );
    }

    @Test
    void findReturnsFreshCachedContent() {
        RedSampleQueryCacheKeyBuilder keyBuilder = new RedSampleQueryCacheKeyBuilder();
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
                "full-sample-body",
                Instant.now().plusSeconds(3600)
        )));

        Optional<String> found = adapter.find(
                "user-1",
                "conn-1",
                RedSampleQueryCacheKeyBuilder.ES_SAMPLE_TOOL,
                args
        );

        assertTrue(found.isPresent());
        assertEquals("full-sample-body", found.get());
    }

    @Test
    void saveStoresFullResultWithTtl() {
        String args = """
                {"partnerId":190,"serverType":"AUDIENCE_CONTAINER","searchType":"SEARCH","indexName":"audience_container_190*"}
                """;
        String longBody = "x".repeat(12_000);

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
        assertEquals(12_000, saved.resultContent().length());
        assertTrue(saved.expiresAt().isAfter(Instant.now()));
    }
}
