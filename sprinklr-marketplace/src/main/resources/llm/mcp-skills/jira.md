## Jira MCP guidance

**Assignee resolution:** Always call `lookupJiraAccountId` when resolving another person's identity (display name or email). Users often misspell names — confirm the account id before search/edit.

**Cloud ID rule (all Jira tools):** Always call `getAccessibleAtlassianResources` first in every turn before any other Jira tool — including create, edit, search, transition, and comment. Never reuse a `cloudId` from conversation history or a prior turn; fetch it fresh each time.

### Required-field rule (search, edit, transition tools)
For search, edit, transition, and comment tools: if a required parameter (issue key, assignee name, etc.) is missing from the user's message, respond with text only and ask for it. **This rule does not apply to create** — see the create workflow below.

### Fetch assigned tickets
When listing tickets for **someone else** (especially if an email is given), always resolve the account with `lookupJiraAccountId` **before** search — never put a raw email into JQL `assignee` first.

1. `getAccessibleAtlassianResources` → get `cloudId`
2. Resolve the assignee if it is not the current user:
   - If the user gives a **display name**, call `lookupJiraAccountId` with that name.
   - If the user gives an **email** (e.g. `parvat.singh@sprinklr.com` or `vaibhav.aggarwal@sprinklr.com`), derive a name from the local part by replacing `.` / `_` with spaces (`parvat singh`, `vaibhav aggarwal`), then call `lookupJiraAccountId` with that name. Use the returned `accountId`.
   - Stop and ask the user if the assignee is not found.
3. Search with JQL using `assignee = currentUser()` for the current user's tickets, or `assignee = "<accountId>"` (from step 2) for someone else's — **not** the email string.

### Query ticket details
- `getAccessibleAtlassianResources` first, then `getJiraIssue` with the issue key (e.g. ITOPS-123) from the user's message or a prior search in the current turn.
- For transitions/comments on an existing issue, you need the issue key or id from the user or tool results.

### Audit log investigation (with RED)
When the user asks to debug, investigate, find root cause, or check audit logs for a ticket:

1. Fetch the full issue first — read **summary, description, all custom fields, and every comment body** before any RED tool call. IDs and environment are often buried in comments.
2. Extract **exact** `partnerId`, `assetId`, and `env` values character-for-character from ticket text. Search for labels like `Partner ID`, `Asset ID`, `Environment`, `env`, `partnerId`, `assetId`, `pId` — these are search hints only; values may appear on any ticket type or project.
3. If required values are missing, respond with text only — do not call RED tools or invent values.
4. When both Jira and RED tools are active, follow the **Jira → RED audit log investigation** cross-workflow guidance — call `red_execute_audit_log_elasticsearch_query` with `username: "test"`, extracted `partnerId`, `env`, and an `assetId` filter query, then infer the issue from the audit log results.

### Issue change history
- `getAccessibleAtlassianResources` first, then `getJiraIssueChangelog` with `cloudId` + issue key.
- Use when the user asks about status history, who changed fields, assignee/priority changes, or what happened on a ticket.
- **Default:** plain markdown — bullets or a short chronological summary.
- **Widgets:** use only for ticket analytics requests (see below).

### Ticket analytics (single ticket — widgets)
Use widgets when the user asks for analytics, insights, or a dashboard for a **specific Jira ticket**. Do **not** use widgets for simple "what changed" narratives or single-fact lookups.

**Required tools (call both):**
1. `getAccessibleAtlassianResources` → fresh `cloudId`
2. `getJiraIssue` with `cloudId` + issue key — for story points, comment count, fix version, current status, created date
3. `getJiraIssueChangelog` with `cloudId` + issue key — for status durations and transition history

**Prescribed widget set (emit 1–3; omit any whose data is missing — never pad):**

| Widget | When to include | Data source |
|--------|----------------|-------------|
| `kpi` (always) | Always | Story Points, Days Open (from created → now or resolved), Comment Count, Fix Version from `getJiraIssue` |
| `bar` | Changelog has ≥2 distinct statuses | Time in each status (days) from changelog timestamps; `xAxisLabel: "Status"`, `yAxisLabel: "Days"` |
| `timeline` | Changelog has ≥3 status-change events | Status transitions only — collapse rapid automation events into one entry; max ~5 events |

**Timeline field names (mandatory):** Each event must use `{ "date": "...", "author": "...", "summary": "..." }` — never `timestamp` or `label`. Dates must be human-readable (e.g. `2026-07-13 18:31 UTC`).

**Do not emit for single-ticket analytics:**
- `pie` / `donut` for change counts by author (not useful for one ticket)
- `line` / `area` for activity over time (cluttered for single tickets)
- Charts where every numeric value is `0`

**Workflow:**
1. Fetch issue + changelog (steps above).
2. Compute metrics **only** from tool results in the current turn.
3. Write a short markdown summary with one actionable insight (e.g. "Spent 10 days in In Progress").
4. Emit a single ` ```widget ` fence with the prescribed widgets (see system prompt example).

**Anti-hallucination:** Every value in widget JSON must be traceable to `getJiraIssue` or `getJiraIssueChangelog` results. If a field is missing (e.g. no fix version), omit that KPI metric or show `"—"` — do not invent data.

### Actions
- **Update status:** `getAccessibleAtlassianResources` → `getTransitionsForJiraIssue` → `transitionJiraIssue`
- **Assign / edit fields:** `getAccessibleAtlassianResources` → `lookupJiraAccountId` (when assignee named) → `editJiraIssue`
- **Add comment:** `getAccessibleAtlassianResources` → `addCommentToJiraIssue`

### Editing Jira issues
**Every edit workflow (including summary, description, assignee, or any field change):**
1. `getAccessibleAtlassianResources` — always first, even if you used a `cloudId` earlier in the conversation
2. `editJiraIssue` with the fresh `cloudId` from step 1

Do NOT call `editJiraIssue` without calling `getAccessibleAtlassianResources` in the same turn first. If an edit fails with a permissions or cloud-access error, call `getAccessibleAtlassianResources` and retry — do not ask the user to reauthorize unless that retry also fails.

### User profile
- Call `atlassianUserInfo` when the user asks about their Jira profile.

### Creating Jira issues

When the user says something like "create a story in ITOPS",never ever give a text only response , follow this 4-phase flow every time.

**CRITICAL — metadata before create (no exceptions):**
`createJiraIssue` is **FORBIDDEN** unless `getJiraProjectIssueTypesMetadata` and `getJiraIssueTypeMetaWithFields` have both been called **in the same turn** immediately before it. This applies to **every** create — first request, follow-up with field values, "create another", retries after errors. **Never** go from `getAccessibleAtlassianResources` directly to `createJiraIssue`. **Never** reuse field IDs, `additionalFieldsKey` values, or `requiredUserInput` from a prior turn or from conversation memory — metadata from an earlier turn is stale and must not be used.

**Phase 1 — Parse intent:** Extract the project key (e.g. ITOPS) and requested issue type (e.g. Story) from the user's message.

**Phase 2 — Validate issue type (tool turn):**
1. `getAccessibleAtlassianResources`
2. `getJiraProjectIssueTypesMetadata` for the project
3. Check whether the requested issue type appears in the results (case-insensitive match on name)
4. If not found → reply with text only, list the available issue types, and stop
5. If found → continue to Phase 3 in the same turn (do not ask the user yet)

**Phase 3 — Fetch required fields (tool turn, then ask user):**
1. `getJiraIssueTypeMetaWithFields` for the confirmed project + issue type
2. Read `requiredUserInput` from the summarized response (`metadataKind: "jiraIssueTypeCreateFieldsSummary"`)
3. Reply with text only — list each required field by display name with its `allowedOptions`
4. Do NOT call `createJiraIssue` yet

**Phase 4 — Create (after user provides values):**
When the user replies with field values (e.g. summary, components, environment), you **MUST** still run the full metadata chain in this turn before creating:
1. `getAccessibleAtlassianResources`
2. `getJiraProjectIssueTypesMetadata` for the project
3. `getJiraIssueTypeMetaWithFields` for the confirmed project + issue type
4. Map the user's values to field keys using **only** the fresh metadata from step 3 (`additionalFieldsKey`, `placement`, `valueShape`)
5. Optional: `lookupJiraAccountId` when the user named an assignee
6. `createJiraIssue`

**Do NOT skip steps 2–3 in Phase 4.** Even if you already fetched metadata in a previous turn, or the user only sent three words like `"test", API, dev`, you **must** call `getJiraProjectIssueTypesMetadata` and `getJiraIssueTypeMetaWithFields` again in this turn before `createJiraIssue`. There is no shortcut.

**First response for create intents (e.g. "create a story in ITOPS"):**
Run Phases 1–3 immediately (tools only, no generic questions). Do NOT ask about summary or description before fetching metadata — only ask for fields that appear in `requiredUserInput`.

**Issue type change = fresh workflow:** If the user switches issue type (Story → Bug, etc.), re-run Phases 2–3 for the new type. Never reuse `requiredUserInput`, field IDs, or allowed options from a different issue type.

**`createJiraIssue` argument shape:**
- Top-level: `cloudId`, `projectKey`, `issueTypeName`, `summary`, `assignee_account_id` (if assigning), and `description` (when the user provides one)
- `additional_fields`: `components`, `priority`, `labels`, and project-specific custom fields from metadata
- Use `additionalFieldsKey` (never the display `name`) as keys inside `additional_fields`
- Follow each field's `placement` and `valueShape` from metadata: `option_object` → `{"value":"Exact Label"}`; `option_array` → `[{"value":"Exact Label"}]`; `version_array` → `[{"name":"Version Name"}]`

**Field values:** If the user did not explicitly name a required field value, respond with text only — list allowed options from metadata and ask them to choose. Never pick an option on your own.

**Create efficiency:** When the user already names a project key, skip `getVisibleJiraProjects`. Do **not** skip `getJiraProjectIssueTypesMetadata` or `getJiraIssueTypeMetaWithFields` — they are mandatory before every `createJiraIssue`.
