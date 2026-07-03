## Jira → RED audit log investigation

**Triggers:** The user provides a Jira issue key (any project) and asks to investigate, debug, find root cause, or check audit logs.

Complete this workflow **in one turn** before responding with a diagnosis. Do not call RED tools until Jira context is fetched.

### Phase 1 — Jira context (before any RED call)

1. `getAccessibleAtlassianResources` → fresh `cloudId`
2. `getJiraIssue` with the issue key from the user's message
3. Read **summary, description, all custom fields, and every comment body** — partner ID, asset ID, and environment are often buried deep in comments
4. **Extract exact values only** — copy character-for-character from ticket text:
   - `partnerId` (numeric)
   - `assetId` (typically long alphanumeric)
   - `env` — must be one of the allowed env values listed in RED guidance; copy the exact token from ticket text
5. **Stop rule:** If `partnerId`, `assetId`, or `env` (when the ticket mentions an environment) cannot be found verbatim in the Jira tool result, respond with text only — list what was found and what is still missing. **Never guess or invent IDs or env values.**

Search hints when scanning ticket text (any project, any issue type): `Partner ID`, `Asset ID`, `Environment`, `env`, `partnerId`, `assetId`, `pId`, and similar labels near numeric or alphanumeric values.

### Phase 2 — RED audit log query

After Phase 1, call **`red_execute_audit_log_elasticsearch_query`** once with values derived **only** from the Jira ticket:

| Argument | Value |
|----------|-------|
| `username` | `"test"` (always use this literal) |
| `partnerId` | exact numeric partner ID from Phase 1 |
| `env` | exact allowlisted env token from Phase 1 |
| `query` | JSON **string** filtering on `assetId` — see query format below |

**Elasticsearch `query` format (required):** The `query` argument must be a JSON **string** whose parsed object has a top-level `"query"` key. The inner query body goes under `"query"` — never start with `"bool"` at the top level.

Correct shape (replace `<assetId>` with the exact value from the ticket):

```json
{"query":{"bool":{"must":[{"term":{"assetId":"<assetId>"}}]}}}
```

When passed as the tool argument, escape it as a string, e.g. `"query":"{\"query\":{\"bool\":{\"must\":[{\"term\":{\"assetId\":\"<assetId>\"}}]}}}"`.

Wrong (will cause a parsing error): `{"bool":{"must":[...]}}` or `"query":"{\"bool\":{...}}"`.

If using `sort` or `size`, they must be **top-level siblings** of `"query"` in the parsed body — never nest them inside `"query"`. See RED guidance for the full search body shape.

See RED guidance for the env allowlist and audit log document schema abbreviations.

### Phase 3 — Diagnosis (especially when the user says debug)

- Read the audit log TOOL result and infer what likely caused the issue described in the ticket
- Correlate audit log events with the ticket symptoms
- When `cg` (change list) is present, walk each entry's `fN` (field name), `oV` (old value), and `nV` (new value) — that is usually where the meaningful state change lives
- Use `cT` (change/event type) and timestamp fields from actual hits
- Cite only values present in TOOL results or the Jira ticket — do not fabricate events, timestamps, or field values
