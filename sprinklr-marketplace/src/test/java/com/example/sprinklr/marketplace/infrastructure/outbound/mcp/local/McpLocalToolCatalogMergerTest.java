package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.local;

import com.example.sprinklr.marketplace.application.service.mcp.McpCatalogTestFixtures;
import com.example.sprinklr.marketplace.domain.model.McpTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.StreamableHttpMcpClient;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.atlassian.JiraIssueChangelogLocalTool;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.catalog.McpCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpLocalToolCatalogMergerTest {

    @Test
    void mergesLocalToolsWithoutOverwritingRemoteTools() {
        McpCatalogLoader catalogLoader = mock(McpCatalogLoader.class);
        JiraIssueChangelogLocalTool changelogTool = new JiraIssueChangelogLocalTool(mock(StreamableHttpMcpClient.class));
        CompositeMcpLocalToolExtension composite = new CompositeMcpLocalToolExtension(List.of(changelogTool));
        McpLocalToolCatalogMerger merger = new McpLocalToolCatalogMerger(catalogLoader, composite);

        var entry = McpCatalogTestFixtures.jiraEntry();
        when(catalogLoader.findById("atlassian-jira")).thenReturn(Optional.of(entry));

        List<McpTool> remote = List.of(
                new McpTool("jira.getJiraIssue", "Get issue", "conn-1", "{}"),
                new McpTool("jira.getJiraIssueChangelog", "Stale local copy", "conn-1", "{}")
        );

        List<McpTool> merged = merger.merge(entry, "conn-1", remote);

        assertEquals(2, merged.size());
        assertTrue(merged.stream().anyMatch(tool -> "jira.getJiraIssueChangelog".equals(tool.name())));
        McpTool changelog = merged.stream()
                .filter(tool -> "jira.getJiraIssueChangelog".equals(tool.name()))
                .findFirst()
                .orElseThrow();
        assertEquals("Stale local copy", changelog.description(), "Remote tool definition must win on name collision");
    }
}
