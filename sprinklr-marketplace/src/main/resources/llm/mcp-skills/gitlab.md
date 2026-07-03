## GitLab MCP guidance

### Required-field rule (all tools)
Required parameters must come from the **user message** or from **tool results earlier in this turn**. Never invent IDs that were not mentioned by the user and did not appear in a prior tool result.

If a required value is missing and cannot be discovered with a list/search tool, respond with text only (no tool calls) and ask the user to supply it.

### Parameter semantics
- `project_id` = numeric GitLab project ID **or** URL-encoded path (e.g. `spr-dev%2Fmy-repo`) — **never** a branch name (`master`, `main`, `develop`, etc.).
- Branch names belong in `ref`, `from`, `to`, `source_branch`, `target_branch`, or commit SHA fields — **not** in `project_id`.
- If the user gives a branch and/or commit but no project, discover the project via `list_merge_requests` or ask: "Please provide the GitLab project path (e.g. spr-dev/my-repo) or project ID."

### Fetch merge request (MR) status
When the user asks about "my MR", an MR title, or pipeline/approval status without a project:

1. Run `whoami` if you need the current user ID to filter MRs.
2. Run `list_merge_requests` **without** `project_id` to find the user's open MRs.
3. Pick the target MR from the results; use its `project_id` and `iid` for all follow-up calls.
4. Call only the **minimal** follow-up tools needed for the question:
   - General MR status → `get_merge_request`
   - Pipeline / CI status → `list_merge_request_pipelines`
   - Approval state → `get_merge_request_approval_state`
   - Review comments → `mr_discussions`
   - Changed files → `list_merge_request_changed_files`
   - Diffs → `get_merge_request_diffs` or `list_merge_request_diffs`
5. Do **not** call the same tools twice in a second wave.
6. Do **not** call `list_projects` or `get_project` unless the user asked about a specific project by name.

When project and MR IID are already known (from the user or a prior tool result), call `get_merge_request` directly.

`list_merge_requests` with `project_id` is for listing MRs in a specific project.

### Fetch pipeline / build status
- Pipeline tools may require `discover_tools` with category `pipelines` first if not already active.
- `list_merge_request_pipelines` requires `project_id` and `merge_request_iid` from the user or a prior MR discovery result.

### Fetch commit / revision details
- `get_commit` requires `project_id` + commit SHA or branch ref.
- `list_commits` requires `project_id`; optional `ref` for branch/tag/range.
- If the user provides a GitLab URL or `group/repo` + SHA, use those values directly.
- If only a SHA or branch is given, discover `project_id` via `list_merge_requests`, project search, or ask the user.

### Compare revisions across branches
- `get_branch_diffs` requires `project_id`, `from`, and `to`.
- MR diff tools (`get_merge_request_diffs`, `list_merge_request_diffs`, etc.) when comparing via an MR.

### Branch activity and deployment-related summaries
- `list_commits`, `get_project_events` for branch activity.
- Commit statuses: `list_commit_statuses`, `create_commit_status`.

### Conflict / merge-check example
For "any conflict for commit X on branch master?" — discover or obtain `project_id` first. Do **not** put `master` in `project_id`. After the project is known (from the user or a tool result), use `get_commit` + `get_branch_diffs` or MR diff tools. There is no dedicated merge-conflict API; infer from diffs and MR state.
