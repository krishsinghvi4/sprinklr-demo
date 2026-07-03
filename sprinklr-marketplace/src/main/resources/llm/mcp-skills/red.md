## RED MCP guidance

### Authentication
- All RED tools require a valid RED access token forwarded as `Authorization: Bearer <token>`.
- Use `red_ping` only when troubleshooting connectivity — not as a default first step for every request.

### Required-field rule (all tools)
Required parameters and filter values must come from the **user message** or from **tool results earlier in this turn** (including Jira `getJiraIssue` output when investigating from a ticket). Never invent IDs, environment values, field names, or filter values that were not mentioned by the user and did not appear in a prior tool result.

If a required value is missing and cannot be discovered from an allowed source, respond with text only (no tool calls) and ask the user to supply it.

### Choosing Elasticsearch vs Mongo vs audit log (accuracy-critical)

Wrong backend choice breaks the entire query. Decide the backend **before** calling any sample or execute tool.

**Primary discriminator (never guess across backends):**

| User provides | Backend | Tools |
|---------------|---------|-------|
| **Index name** (e.g. `audience_container_190*`, `audit_logs_780*`) | **Elasticsearch** | `red_sample_elasticsearch_query` → `red_execute_elastic_search_query` |
| **Collection name** | **Mongo** | `red_sample_mongo_query` → `red_execute_mongo_query` |
| Jira ticket + audit / debug / root-cause | **Dedicated audit ES** | `red_execute_audit_log_elasticsearch_query` (see Audit log queries below) |

**Secondary signals** when the user gives `partnerId` + `serverType` + "fetch data" but the backend is unclear:
- User says "elasticsearch", "index", or names an index pattern → **Elasticsearch**
- User names a collection or says "mongo" / "mongodb" → **Mongo**
- User gives an ES-style `serverType` (e.g. `AUDIENCE_CONTAINER`, `AUDIT_LOGS`) with no collection → **Elasticsearch** (ES tools have no `collectionName` param)
- User gives `serverType` `PAID` / `DEFAULT` / `GLOBAL` with a collection → **Mongo**
- **If still ambiguous** (no index, no collection) → respond with text only and ask: "Elasticsearch index or Mongo collection?" — **never** call both sample tools or pick arbitrarily

**Hard rules:**
- **Index name ⇒ Elasticsearch only.** Never pass `collectionName` on ES tools.
- **Collection name ⇒ Mongo only.** Never pass `indexName`, `searchType`, or other ES-only args on Mongo tools.
- **Never mix backends** in one request — one sample tool, one matching execute tool.
- User-provided index name **wins** over `{lowercase_serverType}_{partnerId}*` heuristics.

### Workflow
- Call the tool that best matches the user's request using the IDs and parameters from allowed sources.
- Do not proactively call lookup or search tools to resolve IDs the user already gave or that appear in same-turn tool results.
- If a tool returns an error, explain it in plain language and ask for corrected input.

**Exception — schema discovery before filtered queries:** For Elasticsearch and Mongo query requests, you MUST call the **correct** sample tool for the chosen backend first in the same turn before the matching execute tool (see **Choosing Elasticsearch vs Mongo** above). This is required discovery, not optional lookup.

### Audit log queries

**Preferred tool — `red_execute_audit_log_elasticsearch_query`:**

Use this dedicated audit log tool (not the generic ES sample/execute pair) when querying audit logs — especially for Jira ticket investigations and debug/root-cause requests.

Required arguments (all from allowed sources unless noted):
- `username`: always `"test"`
- `partnerId`: numeric partner ID from the user message or same-turn tool results
- `env`: must be one of the **allowed env values** below — copy the exact token from ticket text
- `query`: JSON string with a top-level `"query"` key filtering on `assetId` (see **Elasticsearch `query` format** below)

When the investigation started from a Jira ticket, follow the **Jira → RED audit log investigation** cross-workflow guidance (appended when both Jira and RED tools are active).

**Generic ES tools (non-audit or schema discovery):** For other Elasticsearch indices, use the sample → execute pair below with `serverType`, `searchType`, and `indexName` as required by the tool schema.

**Allowed `env` values (exact match, case-sensitive):**
`prod`, `prod0`, `prod2`, `prod3`, `prod4`, `prod5`, `prod6`, `prod8`, `prod11`, `prod12`, `prod15`, `prod16`, `prod17`, `prod18`, `prod19`, `prod21`, `spr-uat`, `azrqa`, `qa6`

Copy the exact token from the user message or Jira ticket text. Do not map vague labels like "production" to `prod` unless the ticket contains that exact allowlist token. Never use an env value not in this list.

### Audit log document schema

Audit log `_source` fields are often **shortened** to save storage. Use this table to decode tool results — it is not a data source; every cited value must come from TOOL results or the Jira ticket.

| Abbrev | Meaning | Notes |
|--------|---------|-------|
| `cg` | Change list | Array of change objects |
| `cgL` | Change list length | Count of entries in `cg` |
| `fN` | Field name | Inside each `cg` element — which field changed |
| `oV` | Old value | Previous value before change |
| `nV` | New value | Value after change |
| `cT` | Change type / event type | Action or event name |
| `assetId` | Asset identifier | Primary filter field for execute queries |
| `assetClass` | Asset class | Type/category of asset |
| `pId` | Partner ID | Corresponds to RED `partnerId` / index `audit_log_p{pId}*` |
| `cId` | Client ID | Client context |
| `uId` | User ID | Acting user |
| `mTs` / `cD` / `mTm` | Timestamps | Event / creation / modification time — read format from actual hits |
| `md` | Module | Subsystem/module code |
| `path` | Code path | Class or job path that performed the action |
| `sIp` | Source IP | Originating IP |

When `cg` is present, explain state changes by walking `fN` / `oV` / `nV` entries. Other abbreviated fields may appear — prefer names and values from actual sample/execute hits.

### Elasticsearch queries (mandatory two-step)

Use only when the backend is **Elasticsearch** (user provided an index name or ES signals above). Never pass `collectionName`.

**Required scope args (sample + execute):**
- `partnerId` — from user message, same-turn tool results, or parsed from index suffix (e.g. `audience_container_190*` → `190`)
- `serverType` — uppercase enum from user (e.g. `AUDIENCE_CONTAINER`, `AUDIT_LOGS`)
- `searchType` — **exact enum**, one of only these three values:
  - `SEARCH` — **default**; use for almost all fetch / filter / list requests
  - `SEARCH_WITH_CLUSTERS` — only when user explicitly asks for cluster-level ES results
  - `ANALYZE` — only when user explicitly asks for ES analysis (aggregations / explain-style)
  - Never invent other spellings or lowercase variants
- `indexName` — user-provided pattern; often `{lowercase_serverType}_{partnerId}*` (e.g. `AUDIT_LOGS` + partner `150` → often `audit_logs_150*`, but **not guaranteed** — prefer the index the user gave)
- `username` — `"test"` when not specified

**Index naming heuristic** (when user omits index but gives `serverType` + `partnerId`):
- Lowercase `serverType`, underscores, append `_{partnerId}*`
- Example: `AUDIENCE_CONTAINER` + `190` → `audience_container_190*`
- If unsure, ask — do not invent exotic patterns

**Step 1 — Sample (always first):**
- Call `red_sample_elasticsearch_query` with scope args above plus optional `env`, `preference`, `host`, `queryExecutorLongQuery`.
- Do **not** pass a `query` parameter — the tool returns up to 5 sample documents with an internal empty query (size 5).
- Identical scope args may return a **cached** sample from a recent call (within the configured TTL, typically 24h) instead of hitting RED again — still read field paths from the returned content whether cached or fresh.
- Read field names from the returned documents. **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_elastic_search_query` with the same scope args plus a `query` filter.
- Use **only field names seen in the sample results**.
- Use **only filter values from allowed sources** (user message or same-turn tool results).
- If the user asked for LinkedIn, Facebook, or any channel/platform filter, build the execute query using the **exact dotted path** from the sample (e.g. `audience.channelTypes`), not a shortened or guessed name.
- For array fields such as `audience.channelTypes`, use a **`terms`** query with a JSON array value, not `term`.
- **Never append `.keyword`** to a field name unless that exact path (including `.keyword`) appears in the sample `_source` hits.
- Read dotted field paths directly from sample `_source` documents — copy those paths exactly into the execute query.

**Worked example:**

> "Fetch 6 most recent LinkedIn audience from elasticsearch, SEARCH searchtype, AUDIENCE_CONTAINER, index name audience_container_190*"

1. **Sample:** `red_sample_elasticsearch_query` with `partnerId: 190`, `serverType: "AUDIENCE_CONTAINER"`, `searchType: "SEARCH"`, `indexName: "audience_container_190*"` — no `query`.
2. Read field paths from sample `_source` hits (e.g. `audience.channelTypes`).
3. **Execute:** `red_execute_elastic_search_query` with same scope args plus `query` JSON string:
   - Filter LinkedIn using exact dotted path from sample (e.g. `terms` on `audience.channelTypes` with `["LINKEDIN"]` — exact enum from sample hits)
   - `sort` on a timestamp field seen in sample (e.g. `createdAt` desc)
   - `size: 6`
   - Top-level `"query"` key wrapping filter body (see format below)

**Elasticsearch search body format:** The `query` argument is a **JSON string** (escaped), not a nested object. When parsed, it is a full Elasticsearch search body. The filter clause lives under a top-level `"query"` key — never pass a bare `"bool"`, `"term"`, or `"match_all"` at the root.

**Critical — `sort`, `size`, and other options are siblings of `query`, not children.** Only filter clauses (`bool`, `term`, `match`, etc.) belong inside `"query"`. Top-level siblings may include: `query`, `sort`, `size`, `from`, `_source`, `aggs`.

Valid:
```json
{"query":{"bool":{"must":[{"term":{"accountId":2085979}},{"term":{"entityType":"AD_SET"}}]}},"sort":[{"createdTime":{"order":"desc"}}],"size":1}
```

Invalid (causes `malformed query` / `parsing_exception` — `sort` and `size` nested inside `query`):
```json
{"query":{"bool":{"must":[{"term":{"accountId":2085979}},{"term":{"entityType":"AD_SET"}}]},"sort":[{"createdTime":{"order":"desc"}}],"size":1}}
```

Minimal filter-only example:
- Correct: `{"query":{"bool":{"must":[{"term":{"assetId":"<id>"}}]}}}`
- Wrong: `{"bool":{"must":[{"term":{"assetId":"<id>"}}]}}`

**Recoverable ES errors:** If a tool result contains `parsing_exception`, `malformed query`, or similar DSL structure errors, **fix the query and call the same execute tool again in this turn** — do not present the raw RED API error as the final answer when the fix is obvious (e.g. move `sort`/`size` out of `query`). When presenting ES results, format epoch-ms timestamp fields as readable dates.

Build filter field paths from sample results and filter values from allowed sources only.

Optional top-level args: `username`, `env`, `preference`, `host`, `queryExecutorLongQuery`.

### Mongo queries (mandatory two-step)

Use only when the backend is **Mongo** (user provided a collection name or Mongo signals above). Never pass `indexName` or `searchType`.

**Required scope args (sample + execute):**
- `partnerId` — from user message or same-turn tool results
- `collectionName` — **always required**; primary Mongo signal
- `queryType` — **exact spelling**, one of:
  - `general`
  - `distinct`
  - `distinct length`
  - `count`
  - `get indexes`
- `serverType` — usually `PAID` or `DEFAULT`; use `GLOBAL` **only** when `partnerId` is `0`

**Step 1 — Sample (always first):**
- Call `red_sample_mongo_query` with scope args above plus optional `sequenceName`, `skip`, `includeFields`, `sortField`, `sortDirection`, `env`.
- Do **not** pass a `query` or `limit` parameter — the tool uses an internal empty filter `{}` and returns up to 5 documents.
- Identical scope args may return a **cached** sample from a recent call (within the configured TTL, typically 24h) instead of hitting RED again — still read field paths from the returned content whether cached or fresh.
- Read field names from the returned documents. **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_mongo_query` with the same scope args plus `query`, `limit`, and other execute parameters.
- Use **only field names seen in the sample results**.
- Use **only filter values from allowed sources**.

### Common operations
- **Health / connectivity:** `red_ping`
- **Audit log query:** `red_execute_audit_log_elasticsearch_query` — use for audit log searches (Jira investigations, debug)
- **Schema sample (before filtered queries):** `red_sample_elasticsearch_query`, `red_sample_mongo_query`
- **Filtered query execution:** `red_execute_elastic_search_query`, `red_execute_mongo_query`
- **Other reads:** tools under `red_get_*` — check tool descriptions for required parameters
- **Sprinklr operations:** client lookup, ES stats, and related tools when the user asks for those capabilities

### Parameter rules
- Never invent IDs, client names, environment values, field names, or filter values.
- Field names for filters must come from sample tool results in the current turn — do not guess schema.
- Prefer the most specific tool for the request; do not broaden to unrelated lookup tools when allowed sources already supply the needed identifiers.
- Confirm Elasticsearch vs Mongo using **Choosing Elasticsearch vs Mongo** before any sample/execute call — backend accuracy is critical.
