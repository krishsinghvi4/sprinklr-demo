package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.gitlab;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabRequiredArgsPreflightGuardTest {

    private GitLabRequiredArgsPreflightGuard guard;

    @BeforeEach
    void setUp() {
        guard = new GitLabRequiredArgsPreflightGuard();
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

        var result = guard.validate("gitlab.get_branch_diffs", args, prompt);
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

        var result = guard.validate("gitlab.get_commit", args, prompt);
        assertTrue(result.allowed());
    }

    @Test
    void blocksWhenProjectIdMissingForProjectScopedTool() {
        String args = """
                {
                  "sha": "abc123"
                }
                """;
        var result = guard.validate("get_commit", args, "show commit abc123 on master branch");
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

        var result = guard.validate("gitlab.get_commit", args, conversationContext);
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

        var result = guard.validate("gitlab.get_commit", args, conversationContext);
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
        var result = guard.validate("gitlab.get_commit", args, "show commit abc123");
        assertFalse(result.allowed());
    }

    @Test
    void ignoresNonProjectScopedTools() {
        var result = guard.validate("gitlab.whoami", "{}", "who am I");
        assertTrue(result.allowed());
    }
}
