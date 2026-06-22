package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JiraIssueTypeCreateRequirementsCacheTest {

    @Test
    void get_returnsCachedRequirementsForMatchingScope() {
        JiraIssueTypeCreateRequirementsCache cache = new JiraIssueTypeCreateRequirementsCache();
        List<JiraIssueTypeMetadataProcessor.RequiredField> fields = List.of(
                new JiraIssueTypeMetadataProcessor.RequiredField(
                        "summary", "Summary", "top_level", ""
                )
        );

        cache.put("conn-1", "ITOPS", "Story", fields);

        var cached = cache.get("conn-1", "ITOPS", "Story");
        assertTrue(cached.isPresent());
        assertEquals(1, cached.get().size());
        assertEquals("Summary", cached.get().get(0).name());
    }

    @Test
    void get_isCaseInsensitiveForProjectAndIssueType() {
        JiraIssueTypeCreateRequirementsCache cache = new JiraIssueTypeCreateRequirementsCache();
        cache.put(
                "conn-1",
                "itops",
                "story",
                List.of(new JiraIssueTypeMetadataProcessor.RequiredField(
                        "summary", "Summary", "top_level", ""
                ))
        );

        assertTrue(cache.get("conn-1", "ITOPS", "Story").isPresent());
    }

    @Test
    void resolve_findsRequirementsCachedByIssueTypeIdWhenCreateUsesName() {
        JiraIssueTypeCreateRequirementsCache cache = new JiraIssueTypeCreateRequirementsCache();
        List<JiraIssueTypeMetadataProcessor.RequiredField> fields = List.of(
                new JiraIssueTypeMetadataProcessor.RequiredField(
                        "summary", "Summary", "top_level", ""
                )
        );
        cache.put("conn-1", "PAID", "26", fields);

        var resolved = cache.resolve("conn-1", "PAID", List.of("Story"));
        assertTrue(resolved.isPresent());
        assertEquals(1, resolved.get().size());
    }

    @Test
    void cacheKey_normalizesScopeParts() {
        assertEquals(
                "conn-1|itops|story",
                JiraIssueTypeCreateRequirementsCache.cacheKey("conn-1", "ITOPS", "Story")
        );
    }
}
