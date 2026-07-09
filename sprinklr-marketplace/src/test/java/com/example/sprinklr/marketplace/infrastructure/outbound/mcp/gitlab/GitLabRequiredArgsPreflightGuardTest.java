package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.gitlab;

import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.document.McpConnectionDocument;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.repository.McpConnectionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabRequiredArgsPreflightGuardTest {

    private GitLabRequiredArgsPreflightGuard guard;
    private McpConnectionRepository connectionRepository;

    @BeforeEach
    void setUp() {
        connectionRepository = mock(McpConnectionRepository.class);
        guard = new GitLabRequiredArgsPreflightGuard(connectionRepository);
        when(connectionRepository.findById("conn-gitlab")).thenReturn(Optional.of(
                new McpConnectionDocument(
                        "conn-gitlab",
                        "user-1",
                        "gitlab-mcp",
                        "gitlab",
                        "encrypted",
                        null,
                        null,
                        "CONNECTED",
                        List.of(),
                        Instant.now(),
                        null,
                        null,
                        null,
                        null
                )
        ));
    }

    @Test
    void blocksWhenProjectIdIsBranchNameFromPrompt() {
        String args = """
                {
                  "project_id": "master",
                  "from": "master",
                  "to": "e454499742155df35e3333bea031925df7a72ba5"
                }
                """;
        String prompt = "for branch master is there any conflict for commit e454499742155df35e3333bea031925df7a72ba5?";

        var result = guard.validate("gitlab.get_branch_diffs", args, prompt, "conn-gitlab");
        assertFalse(result.allowed());
    }

    @Test
    void allowsWhenUserSpecifiedProjectPath() {
        String args = """
                {
                  "project_id": "spr-dev/my-repo",
                  "sha": "e454499742155df35e3333bea031925df7a72ba5"
                }
                """;
        String prompt = "in project spr-dev/my-repo show commit e454499742155df35e3333bea031925df7a72ba5";

        var result = guard.validate("gitlab.get_commit", args, prompt, "conn-gitlab");
        assertTrue(result.allowed());
    }

    @Test
    void blocksWhenProjectIdMissingForProjectScopedTool() {
        String args = """
                {
                  "sha": "abc123"
                }
                """;
        var result = guard.validate("get_commit", args, "show commit abc123 on master branch", "conn-gitlab");
        assertFalse(result.allowed());
    }

    @Test
    void allowsWhenProjectPathProvidedInEarlierTurnAndUserConfirms() {
        String args = """
                {
                  "project_id": "sprinklr/main/sprinklr.app",
                  "sha": "e454499742155df35e3333bea031925df7a72ba5"
                }
                """;
        String conversationContext = """
                for branch master is there any conflict for commit e454499742155df35e3333bea031925df7a72ba5?
                https://gitlab.com/sprinklr/main/sprinklr.app/-/commit/e454499742155df35e3333bea031925df7a72ba5
                Is the project path sprinklr/main/sprinklr.app? If so, please confirm.
                yes
                """;

        var result = guard.validate("gitlab.get_commit", args, conversationContext, "conn-gitlab");
        assertTrue(result.allowed());
    }

    @Test
    void allowsUrlEncodedProjectIdWhenPathInConversation() {
        String args = """
                {
                  "project_id": "sprinklr%2Fmain%2Fsprinklr.app",
                  "sha": "abc123"
                }
                """;
        String conversationContext = "check commit abc123 in sprinklr/main/sprinklr.app on master";

        var result = guard.validate("gitlab.get_commit", args, conversationContext, "conn-gitlab");
        assertTrue(result.allowed());
    }

    @Test
    void blocksWhenProjectIdNotMentionedInPrompt() {
        String args = """
                {
                  "project_id": "99999",
                  "sha": "abc123"
                }
                """;
        var result = guard.validate("gitlab.get_commit", args, "show commit abc123", "conn-gitlab");
        assertFalse(result.allowed());
    }

    @Test
    void allowsProjectIdDiscoveredFromSameTurnToolResults() {
        String args = """
                {
                  "project_id": "198",
                  "merge_request_iid": "263763"
                }
                """;
        String conversationContext = """
                show pipeline status for my latest MR
                [tools-called-this-turn: gitlab.list_merge_requests]
                [tool-results-this-turn:
                list_merge_requests -> [{"id":263763,"iid":263763,"project_id":198,"title":"Fix auth"}]
                ]""";

        var result = guard.validate("gitlab.list_merge_request_pipelines", args, conversationContext, "conn-gitlab");
        assertTrue(result.allowed());
    }

    @Test
    void allowsMergeRequestIidDiscoveredFromToolResults() {
        String args = """
                {
                  "project_id": "198",
                  "merge_request_iid": "263763"
                }
                """;
        String conversationContext = """
                status of my MR
                [tool-results-this-turn:
                list_merge_requests -> {"project_id":198,"iid":263763}
                ]""";

        var result = guard.validate("gitlab.get_merge_request", args, conversationContext, "conn-gitlab");
        assertTrue(result.allowed());
    }

    @Test
    void blocksInventedProjectIdOnMrDiscussionsWhenNotDiscovered() {
        String args = """
                {
                  "project_id": "198",
                  "merge_request_iid": "263763"
                }
                """;
        var result = guard.validate("gitlab.mr_discussions", args, "any comments on my MR?", "conn-gitlab");
        assertFalse(result.allowed());
    }

    @Test
    void blocksInventedProjectIdWhenNotInPromptOrToolResults() {
        String args = """
                {
                  "project_id": "198",
                  "merge_request_iid": "263763"
                }
                """;
        var result = guard.validate(
                "gitlab.list_merge_request_pipelines", args, "pipeline status please", "conn-gitlab");
        assertFalse(result.allowed());
    }

    @Test
    void ignoresNonProjectScopedTools() {
        var result = guard.validate("gitlab.whoami", "{}", "who am I", "conn-gitlab");
        assertTrue(result.allowed());
    }
}
