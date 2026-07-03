package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.red;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedRequiredArgsPreflightGuardTest {

    private RedRequiredArgsPreflightGuard guard;
    private McpConnectionRepository connectionRepository;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(McpConnectionRepository.class);
        guard = new RedRequiredArgsPreflightGuard(connectionRepository, new RedEnvironmentAllowlist());
        when(connectionRepository.findById("conn-red")).thenReturn(Optional.of(
                new McpConnectionDocument(
                        "conn-red",
                        "user-1",
                        "red-mcp",
                        "red",
                        "encrypted",
                        null,
                        null,
                        "CONNECTED",
                        List.of(),
                        Instant.now(),
                        null,
                        null,
                        null
                )
        ));
    }

    @Test
    void blocksDedicatedAuditLogToolWithoutGetJiraIssueThisTurn() {
        String args = """
                {
                  "username": "test",
                  "partnerId": 708,
                  "env": "prod16",
                  "query": "{\\"query\\":{\\"term\\":{\\"assetId\\":\\"abc123\\"}}}"
                }
                """;
        String prompt = "debug PAID-69538 from audit logs";

        var result = guard.validate("red.red_execute_audit_log_elasticsearch_query", args, prompt, "conn-red");
        assertFalse(result.allowed());
    }

    @Test
    void allowsDedicatedAuditLogToolAfterGetJiraIssue() {
        String args = """
                {
                  "username": "test",
                  "partnerId": 708,
                  "env": "prod16",
                  "query": "{\\"query\\":{\\"term\\":{\\"assetId\\":\\"6a1fd4f900bff103f807284b\\"}}}"
                }
                """;
        String prompt = """
                debug PAID-69538 from audit logs
                [tools-called-this-turn: jira.getAccessibleAtlassianResources, jira.getJiraIssue]
                """;

        var result = guard.validate("red.red_execute_audit_log_elasticsearch_query", args, prompt, "conn-red");
        assertTrue(result.allowed());
    }

    @Test
    void blocksInvalidEnv() {
        String args = """
                {
                  "username": "test",
                  "partnerId": 190,
                  "env": "production",
                  "query": "{\\"query\\":{\\"term\\":{\\"assetId\\":\\"abc\\"}}}"
                }
                """;
        String prompt = """
                debug audit logs
                [tools-called-this-turn: jira.getJiraIssue]
                """;

        var result = guard.validate("red.red_execute_audit_log_elasticsearch_query", args, prompt, "conn-red");
        assertFalse(result.allowed());
    }

    @Test
    void blocksInvalidEnvOnGenericAuditLogSample() {
        String args = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIT_LOG",
                  "searchType": "SEARCH",
                  "indexName": "audit_log_p190*",
                  "env": "production"
                }
                """;
        String prompt = "partner 190 production environment audit logs";

        var result = guard.validate("red.red_sample_elasticsearch_query", args, prompt, "conn-red");
        assertFalse(result.allowed());
    }

    @Test
    void blocksDedicatedAuditLogToolWithoutEnv() {
        String args = """
                {
                  "username": "test",
                  "partnerId": 190,
                  "query": "{\\"query\\":{\\"term\\":{\\"assetId\\":\\"abc123def456\\"}}}"
                }
                """;
        String prompt = """
                debug audit logs
                [tools-called-this-turn: jira.getJiraIssue]
                """;

        var result = guard.validate("red.red_execute_audit_log_elasticsearch_query", args, prompt, "conn-red");
        assertFalse(result.allowed());
    }

    @Test
    void allowsGenericAuditLogSampleWithoutPartnerIdProvenanceCheck() {
        String args = """
                {
                  "partnerId": 999,
                  "serverType": "AUDIT_LOG",
                  "searchType": "SEARCH",
                  "indexName": "audit_log_p999*"
                }
                """;

        var result = guard.validate("red.red_sample_elasticsearch_query", args, "check audit logs", "conn-red");
        assertTrue(result.allowed());
    }

    @Test
    void allowsGenericAuditLogExecuteWithoutAssetIdProvenanceCheck() {
        String args = """
                {
                  "partnerId": 190,
                  "serverType": "AUDIT_LOG",
                  "searchType": "SEARCH",
                  "indexName": "audit_log_p190*",
                  "query": "{\\"query\\":{\\"term\\":{\\"assetId\\":\\"inventedAssetId\\"}}}"
                }
                """;
        String prompt = "partnerId 190 audit logs";

        var result = guard.validate("red.red_execute_elastic_search_query", args, prompt, "conn-red");
        assertTrue(result.allowed());
    }

    @Test
    void allowsNonAuditLogServerTypeWithoutValidation() {
        String args = """
                {
                  "partnerId": 999,
                  "serverType": "OTHER",
                  "searchType": "SEARCH",
                  "indexName": "other_index*"
                }
                """;

        var result = guard.validate("red.red_sample_elasticsearch_query", args, "lookup data", "conn-red");
        assertTrue(result.allowed());
    }

    @Test
    void jiraGetIssueCalledThisTurnDetectsFullyQualifiedAndBareNames() {
        assertTrue(RedRequiredArgsPreflightGuard.jiraGetIssueCalledThisTurn(
                "[tools-called-this-turn: jira.getAccessibleAtlassianResources, jira.getJiraIssue]"));
        assertTrue(RedRequiredArgsPreflightGuard.jiraGetIssueCalledThisTurn(
                "[tools-called-this-turn: getJiraIssue]"));
        assertFalse(RedRequiredArgsPreflightGuard.jiraGetIssueCalledThisTurn(
                "[tools-called-this-turn: jira.getAccessibleAtlassianResources]"));
    }
}
