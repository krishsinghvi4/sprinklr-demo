## GitLab MCP guidance

### Required-field rule (all tools)
Required parameters must come from the **user message** or from **tool results earlier in this turn**. Never invent IDs that were not mentioned by the user and did not appear in a prior tool result.

If a required value is missing and cannot be discovered with a list/search tool, respond with text only (no tool calls) and ask the user to supply it.

### Parameter semantics
- `project_id` = numeric GitLab project ID **or** URL-encoded path (e.g. `spr-dev%2Fmy-repo`) — **never** a branch name (`master`, `main`, `develop`, etc.).
- Branch names belong in `ref`, `from`, `to`, `source_branch`, `target_branch`, or commit SHA fields — **not** in `project_id`.
- If the user gives a branch and/or commit but no project, discover the project via `list_merge_requests` or ask: "Please provide the GitLab project path (e.g. spr-dev/my-repo) or project ID."
- `author_username` = GitLab **login** only (e.g. `mayank.kumar`). **Never** a display name like `Mayank Kumar`.

### MRs authored by a named other person
Use this path **only** when the prompt asks to list or find merge requests authored by a **named other person** (e.g. "open MRs by Mayank Kumar", "MRs authored by Vaibhav Aggarwal in project X").

**Do not** use this path for "my MRs" (see below), an MR URL / known project + IID, or when the user already gave an exact login.

1. **Normalize** the display name to the usual login shape: lowercase, spaces → `.` (e.g. `Vaibhav Aggarwal` → `vaibhav.aggarwal`, `Mayank Kumar` → `mayank.kumar`). Pass that string into `get_users` — **not** as `author_username` yet, and not as the raw display name.
2. **Confirm via `get_users`** before any author-filtered MR list.
3. **Confirmed (single clear match)** — take the returned `username` (may be the base login or a suffix variant like `mayank.kumar1`) and call `list_merge_requests` with `author_username=<that username>` plus `project_id` / `state` as needed.
4. **Multiple candidates** — ask the user which GitLab username to use; do not pick silently.
5. **No match** — ask for the exact GitLab username. Do **not** call `list_merge_requests` with the display name or an unconfirmed guessed login. Do **not** claim "no MRs" without a confirmed author.

If the user already provides an exact login (e.g. `mayank.kumar`), skip `get_users` and pass it directly as `author_username`.

### Fetch merge request (MR) status
When the user asks about **"my MR"** / **"my open merge requests"** (current user only), an MR title, or pipeline/approval status without a project:

1. Run `whoami` if you need the current user ID/login to filter MRs. **Do not** call `get_users` for "my MRs" unless the same prompt also names another person.
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

When project and MR IID are already known (from the user, an MR URL like `.../merge_requests/<iid>`, or a prior tool result), call `get_merge_request` directly — no `get_users` needed.

`list_merge_requests` with `project_id` is for listing MRs in a specific project. For MRs by a named other person, follow **MRs authored by a named other person** first.

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

### MR analytics (single merge request — widgets)
Use widgets when the user asks for analytics, insights, or a dashboard for a **specific merge request**. Do **not** force charts for simple MR status lookups.

**Required tools (call all needed for the question):**
1. `get_merge_request` — for created_at, merged_at, state, author; compute days open
2. `mr_discussions` — for comment count
3. `get_merge_request_approval_state` — for approvals/reviews count

**Prescribed widget set (emit 1–3; omit any whose data is missing — never pad):**

| Widget | When to include | Data source |
|--------|----------------|-------------|
| `kpi` (always) | Always | Days Open, Merged At (human date or "Open"), Comments, Approvals from tools above |
| `timeline` | Key dates available (opened, first review, merged) | Lifecycle events — max ~5 events |
| `table` | Multiple approval rules exist | Rule, Required, Approved, Approvers from `get_merge_request_approval_state` |

**Timeline field names (mandatory):** Each event must use `{ "date": "...", "author": "...", "summary": "..." }` — never `timestamp` or `label`. Dates must be human-readable (e.g. `2026-07-10 16:00 UTC`).

**Do not emit for single-MR analytics:**
- `pie` / `bar` / `donut` / `line` / `area` — a single MR has no distribution or time series to chart
- Charts where every numeric value is `0`
- Charting approval rules as slices or bars

**Workflow:**
1. Fetch MR + discussions + approval state (steps above).
2. Compute metrics **only** from tool results in the current turn.
3. Write a short markdown summary with one actionable insight (e.g. "Open 7 days with 0 approvals").
4. Emit a single ` ```widget ` fence with the prescribed widgets (see system prompt example).

**Anti-hallucination:** Every value in widget JSON must trace to tool output. Do not invent pipeline statuses, counts, or dates.

### Analytics (multi-MR / project-level — widgets)
Use widgets only when data spans **multiple** MRs, pipelines, or commits — not for a single MR.

**Good widget candidates:**
- Pipeline status distribution → `list_merge_request_pipelines` → `pie` or `bar` (passed/failed/canceled counts across multiple runs)
- Commit frequency over time → `list_commits` → `line` or `area` (commits per day/week)
- MR state breakdown across a project → `list_merge_requests` → `pie` (open/merged/closed)

**Anti-patterns (never do these):**
- Chart approval rules as `pie` / `bar` / `donut`
- Emit any chart where every numeric value is `0` — omit that chart or use `kpi` / `table` instead
