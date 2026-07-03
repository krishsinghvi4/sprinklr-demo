package com.example.sprinklr.marketplace.infrastructure.outbound.mcp.gitlab;

import com.example.sprinklr.marketplace.domain.model.McpInvocation;
import com.example.sprinklr.marketplace.domain.port.outbound.McpInvocationPreflightPort.PreflightResult;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.McpInvocationPreflightStrategy;
import com.example.sprinklr.marketplace.infrastructure.outbound.mcp.preflight.UserPromptValueMatcher;
import com.example.sprinklr.marketplace.infrastructure.outbound.persistence.McpConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Blocks GitLab tool calls when {@code project_id} is missing, invented, or confused with a branch name.
 * Values discovered from same-turn tool results are allowed.
 */
@Component
public class GitLabRequiredArgsPreflightGuard implements McpInvocationPreflightStrategy {

    private static final Logger log = LoggerFactory.getLogger(GitLabRequiredArgsPreflightGuard.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String GITLAB_PREFIX = "gitlab";

    private final McpConnectionRepository connectionRepository;

    private static final Set<String> COMMON_BRANCH_NAMES = Set.of(
            "master", "main", "develop", "dev", "staging", "production"
    );

    /** Tools that require project_id in arguments — block when it is entirely absent. */
    private static final Set<String> PROJECT_ID_REQUIRED_TOOLS = Set.of(
            "get_commit",
            "get_branch_diffs",
            "list_commits",
            "get_commit_diff",
            "get_merge_request",
            "get_file_contents",
            "list_merge_request_pipelines",
            "get_repository_tree",
            "get_project",
            "get_project_events",
            "list_commit_statuses",
            "create_commit_status",
            "get_branch",
            "list_branches",
            "list_pipelines",
            "get_pipeline"
    );

    public GitLabRequiredArgsPreflightGuard(McpConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    @Override
    public PreflightResult validate(McpInvocation invocation, String userPrompt) {
        return validate(
                invocation.toolName(),
                invocation.argumentsJson(),
                userPrompt,
                invocation.serverId()
        );
    }

    PreflightResult validate(String toolName, String argumentsJson, String userPrompt, String connectionId) {
        if (!isGitLabTool(toolName, connectionId)) {
            return PreflightResult.allow();
        }
        if (userPrompt == null || userPrompt.isBlank()) {
            return PreflightResult.allow();
        }

        String bareToolName = bareToolName(toolName);

        try {
            JsonNode root = OBJECT_MAPPER.readTree(argumentsJson);
            JsonNode projectIdNode = root.path("project_id");
            boolean hasProjectId = !projectIdNode.isMissingNode() && !projectIdNode.isNull();
            String projectId = hasProjectId ? projectIdNode.asText("").trim() : "";

            if (PROJECT_ID_REQUIRED_TOOLS.contains(bareToolName)
                    && (!hasProjectId || projectId.isBlank())) {
                return PreflightResult.block(
                        "The user did not provide a GitLab project_id. Do NOT call " + bareToolName
                                + ". Reply to the user and ask for the GitLab project path (e.g. spr-dev/my-repo) "
                                + "or numeric project ID."
                );
            }

            if (!projectId.isBlank()) {
                PreflightResult projectIdResult = validateProjectId(bareToolName, projectId, userPrompt);
                if (!projectIdResult.allowed()) {
                    return projectIdResult;
                }
            }

            JsonNode mergeRequestIidNode = root.path("merge_request_iid");
            if (!mergeRequestIidNode.isMissingNode() && !mergeRequestIidNode.isNull()) {
                String mergeRequestIid = mergeRequestIidNode.asText("").trim();
                if (!mergeRequestIid.isBlank()
                        && !UserPromptValueMatcher.valueAllowed(userPrompt, mergeRequestIid)) {
                    return PreflightResult.block(
                            "merge_request_iid \"" + mergeRequestIid + "\" was not provided by the user or "
                                    + "discovered from a prior tool result this turn. Do NOT call " + bareToolName
                                    + ". Discover the MR first (e.g. list_merge_requests) or ask the user."
                    );
                }
            }

            JsonNode shaNode = root.path("sha");
            if (!shaNode.isMissingNode() && !shaNode.isNull()) {
                String sha = shaNode.asText("").trim();
                if (!sha.isBlank() && !UserPromptValueMatcher.valueAllowed(userPrompt, sha)) {
                    return PreflightResult.block(
                            "commit SHA \"" + sha + "\" was not provided by the user or discovered from a prior "
                                    + "tool result this turn. Do NOT call " + bareToolName + "."
                    );
                }
            }

            return PreflightResult.allow();
        } catch (Exception exception) {
            log.warn("[GitLabPreflight] Failed to validate {} arguments: {}", bareToolName, exception.getMessage());
            return PreflightResult.block(
                    "Could not validate GitLab tool arguments. Ask the user for the project path or ID before calling "
                            + bareToolName + "."
            );
        }
    }

    private PreflightResult validateProjectId(String bareToolName, String projectId, String conversationContext) {
        String branchInPrompt = UserPromptValueMatcher.branchNameMentionedInPrompt(conversationContext);
        if (branchInPrompt != null && projectId.equalsIgnoreCase(branchInPrompt)) {
            return PreflightResult.block(
                    "\"" + projectId + "\" is a branch name from the user's message, not a project_id. "
                            + "Do NOT call " + bareToolName + ". Ask the user for the GitLab project path "
                            + "(e.g. group/repo) or numeric project ID."
            );
        }

        if (COMMON_BRANCH_NAMES.contains(projectId.toLowerCase(Locale.ROOT))
                && !UserPromptValueMatcher.valueAllowed(conversationContext, projectId)) {
            return PreflightResult.block(
                    "\"" + projectId + "\" looks like a branch name, not a project_id. "
                            + "Do NOT call " + bareToolName + ". Ask the user for the GitLab project path or ID."
            );
        }

        if (!UserPromptValueMatcher.valueAllowed(conversationContext, projectId)) {
            return PreflightResult.block(
                    "project_id \"" + projectId + "\" was not provided by the user or discovered from a prior "
                            + "tool result this turn. Do NOT call " + bareToolName
                            + ". Discover the project/MR first (e.g. list_merge_requests) or ask the user."
            );
        }

        return PreflightResult.allow();
    }

    private boolean isGitLabTool(String toolName, String connectionId) {
        if (toolName == null) {
            return false;
        }
        if (toolName.startsWith(GITLAB_PREFIX + ".")) {
            return true;
        }
        return connectionRepository.findById(connectionId)
                .map(connection -> GITLAB_PREFIX.equals(connection.serverIdPrefix()))
                .orElse(false);
    }

    private static String bareToolName(String toolName) {
        int separator = toolName.indexOf('.');
        return separator > 0 ? toolName.substring(separator + 1) : toolName;
    }
}
