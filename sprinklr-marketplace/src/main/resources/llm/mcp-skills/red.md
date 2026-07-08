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
- **If still ambiguous for a single sub-request** (no index, no collection, and the user did not ask for both backends) → respond with text only and ask: "Elasticsearch index or Mongo collection?" — do not guess.

**Hard rules:**
- **Index name ⇒ Elasticsearch only.** Never pass `collectionName` on ES tools.
- **Collection name ⇒ Mongo only.** Never pass `indexName`, `searchType`, or other ES-only args on Mongo tools.
- User-provided index name **wins** over `{lowercase_serverType}_{partnerId}*` heuristics.

### Multi-part requests

When the user asks for **multiple fetches** in one message (multiple filters, multiple items, both Mongo and Elasticsearch, or a list of sub-requests):

**Sample once, execute many (per backend + scope):**
- A **scope** is the set of scope args that identify the target: `partnerId`, `serverType`, and either `indexName` (ES) or `collectionName` (Mongo), plus other sample/execute scope fields as required by the tool schema.
- For each backend + scope, call the matching **sample tool exactly once** at the **start** of that chain — before any execute for that same scope.
- **Do not** call sample again before a second execute when scope args are unchanged — only the `query` filter differs.
- **Do** call sample again when scope args change (different collection, index, `serverType`, etc.) or when switching backend (Mongo vs Elasticsearch).

**Repeat execute — one sub-request per execute call:**
- Call the matching **execute** tool once per sub-request (each distinct filter or fetch the user asked for).
- **Never combine** multiple user-requested items into a single execute `query` when they asked for separate results (e.g. "A and B" → two execute calls, not one `terms` filter with both values).
- Applies to Elasticsearch and Mongo: if the user names two channels, two entity types, two collections, or two filters, run **separate** execute calls — one filter value (or one logical fetch) per call.
- Keep calling tools across agentic iterations until every sub-request is done; only then reply with the combined answer.
- If a required arg is missing for one sub-request, ask for it (text only).

**Cross-backend order (Mongo before Elasticsearch):**
When the user wants **both Mongo and Elasticsearch** in one message, run complete chains in this order — do not interleave backends and do not call both sample tools upfront:

1. `red_sample_mongo_query` (once for that Mongo scope)
2. `red_execute_mongo_query` (one or more times if multiple Mongo sub-requests share the same scope)
3. `red_sample_elasticsearch_query` (once for that ES scope)
4. `red_execute_elastic_search_query` (one or more times if multiple ES sub-requests share the same scope)

Sample each backend immediately before its execute chain, not before both chains.

### Workflow
- Call the tool that best matches the user's request using the IDs and parameters from allowed sources.
- Do not proactively call lookup or search tools to resolve IDs the user already gave or that appear in same-turn tool results.
- If a tool returns an error, explain it in plain language and ask for corrected input.

**Exception — schema discovery before filtered queries:** For Elasticsearch and Mongo query requests, you MUST call the **correct** sample tool once per scope before the matching execute tool(s) for that scope (see **Choosing Elasticsearch vs Mongo** and **Multi-part requests** above). This is required discovery, not optional lookup.

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

**Step 1 — Sample (once per scope, before any execute for that scope):**
- Call `red_sample_elasticsearch_query` with scope args above plus optional `env`, `preference`, `host`, `queryExecutorLongQuery`.
- Do **not** pass a `query` parameter — the tool returns up to 2 sample documents with an internal empty query (configurable server-side, default 2).
- Call sample **only once** at the start of each ES scope chain — not again before a second execute with the same scope (see **Multi-part requests**).
- Identical scope args may return a **cached** sample from a recent call (within the configured TTL, typically 24h) instead of hitting RED again — still read field paths from the returned content whether cached or fresh.
- A **Filter field paths** block is appended automatically from **this** sample's JSON. The schema differs per `serverType`/index — never assume field names from examples, prior turns, or a different serverType.
- Read field names from that appended path list (and the sample documents). **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Field path rules (all serverTypes):**
- Use **only** dotted paths listed in the appended **Filter field paths** section for execute filters.
- Dot-join each nesting level (`parent.child`). For arrays of objects, use `parent.arrayField.leaf` — never `[0]` (e.g. `audience.filterGroups.segmentId`, not `filterGroups[0].segmentId`).
- Never use Elasticsearch hit metadata (`_id`, `_index`) for business filters unless that exact path appears in the appended list.
- When the user asks for an ID or segment, pick the matching path from the **id / segment** subsection of the index — do not default to `_id`.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_elastic_search_query` with the same scope args plus a `query` filter.
- For multiple ES sub-requests with the **same scope**, call execute again with a different `query` — do not re-sample.
- Use **only** field paths from the appended **Filter field paths** list — do not unnecessarily append `.keyword`.
- Use **only filter values from allowed sources** (user message or same-turn tool results).

**AD_ENTITY — paid initiative, ad set, ad variant:**
- When the user asks for paid initiative, ad set, or ad variant from Elasticsearch, `serverType` is usually `AD_ENTITY`.
- **Always** filter by `entityType` in the execute `query` using the exact enum from sample hits:
  - paid initiative → `entityType`: `PAID_INITIATIVE`
  - ad set → `entityType`: `AD_SET`
  - ad variant → `entityType`: `AD_VARIANT`
- Use the exact field path for `entityType` as it appears in sample `_source` (often `entityType` at the root).
- If the user asks for more than one of these (e.g. ad set and paid initiative), make **separate** execute calls — one `entityType` per call (see **Multi-part requests**).

**Channel / platform filters (e.g. audience):**
- If the user asked for one channel or platform (e.g. LinkedIn), filter using the **exact dotted path** from the sample (e.g. `audience.channelTypes`) with a **single** value in `term` or `terms` with a one-element array.
- If the user asked for **multiple** channels or platforms in one message (e.g. "LinkedIn and Facebook audience"), make **separate** execute calls — one channel per call. **Do not** combine them in one query (wrong: `terms` on `audience.channelTypes` with `["LINKEDIN","FACEBOOK"]` in a single execute).
- For array fields such as `audience.channelTypes`, use **`terms`** with a JSON array when filtering one multi-value field — but still **one user-requested value per execute call** when they asked for separate fetches.
- **Never append `.keyword`** to a field name unless that exact path (including `.keyword`) appears in the sample `_source` hits.
- Read dotted field paths directly from sample `_source` documents — copy those paths exactly into the execute query.

**Worked examples:**

**Single channel (one execute):**

> "Fetch 6 most recent LinkedIn audience from elasticsearch, SEARCH searchtype, AUDIENCE_CONTAINER, index name audience_container_190*"

1. **Sample:** `red_sample_elasticsearch_query` with `partnerId: 190`, `serverType: "AUDIENCE_CONTAINER"`, `searchType: "SEARCH"`, `indexName: "audience_container_190*"` — no `query`.
2. Read field paths from sample `_source` hits (e.g. `audience.channelTypes`).
3. **Execute:** `red_execute_elastic_search_query` with same scope args plus `query` JSON string:
   - Filter LinkedIn using exact dotted path from sample (e.g. `terms` on `audience.channelTypes` with `["LINKEDIN"]` only — exact enum from sample hits)
   - `sort` on a timestamp field seen in sample (e.g. `createdAt` desc)
   - `size: 6`
   - Top-level `"query"` key wrapping filter body (see format below)

**Multiple channels or items (separate execute per item):**

> "Fetch most recent LinkedIn and Facebook audience from elasticsearch, partner id 190"

1. **Sample once** for the ES scope (`AUDIENCE_CONTAINER`, `audience_container_190*`, etc.).
2. **Execute #1:** filter `audience.channelTypes` (or path from sample) for **LinkedIn only** — `size: 1` or as user asked.
3. **Execute #2:** same scope args, new `query` filtering **Facebook only** — do not merge both channels into one execute.

Same rule for Mongo: two collections or two filters the user named → two `red_execute_mongo_query` calls, not one combined filter.

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

**Step 1 — Sample (once per scope, before any execute for that scope):**
- Call `red_sample_mongo_query` with scope args above plus optional `sequenceName`, `skip`, `includeFields`, `sortField`, `sortDirection`, `env`.
- Do **not** pass a `query` or `limit` parameter — the tool uses an internal empty filter `{}` and returns up to 2 documents (configurable server-side, default 2).
- Call sample **only once** at the start of each Mongo scope chain — not again before a second execute with the same scope (see **Multi-part requests**).
- Identical scope args may return a **cached** sample from a recent call (within the configured TTL, typically 24h) instead of hitting RED again — still read field paths from the returned content whether cached or fresh.
- A **Filter field paths** block is appended automatically from **this** sample's documents. The schema differs per collection/serverType — never assume field names from examples or prior turns.
- Read field names from that appended path list. **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_mongo_query` with the same scope args plus `query`, `limit`, and other execute parameters.
- For multiple Mongo sub-requests with the **same scope**, call execute again with a different `query` — do not re-sample.
- If the user asked for **multiple** distinct fetches in one message, use **separate** execute calls — do not combine into one `query` (see **Multi-part requests**).
- Use **only** dotted field paths from the appended **Filter field paths** list.
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
- Field names for filters must come from the appended **Filter field paths** block in sample tool results this turn — do not guess schema.
- Prefer the most specific tool for the request; do not broaden to unrelated lookup tools when allowed sources already supply the needed identifiers.
- Confirm Elasticsearch vs Mongo using **Choosing Elasticsearch vs Mongo** before any sample/execute call — backend accuracy is critical.

### User-configured query allowlists

When the user has configured query allowlists on Profile for their RED connection, those values are **prepended to RED query tool descriptions** (Elasticsearch sample/execute tools get ES serverTypes; Mongo sample/execute tools get Mongo serverTypes and collections).

- Elasticsearch serverTypes on ES tools mean **Elasticsearch only** — use ES sample/execute tools, not Mongo.
- Mongo serverTypes and collections on Mongo tools mean **Mongo only** — use Mongo sample/execute tools, not Elasticsearch.
- Prefer `serverType` and `collectionName` values from the tool description allowlist when building scope args.
- If `partnerId`, backend choice (ES vs Mongo), `serverType`, or `collectionName` is still missing or ambiguous, respond with text only (no tool calls) and ask the user — offer options from the allowlist in the relevant tool description when applicable.

### Analytics (widgets — when useful)
Use widgets when RED query or audit results support quantitative patterns clearer as charts than prose or HTML tables. Do **not** force charts for simple record lookups or single-document answers.

**Good widget candidates:**
- Audit log event counts by action type or user → `red_execute_audit_log_elasticsearch_query` → `bar` or `pie`
- Result distributions from ES/Mongo execute tools → `bar` or `pie` (counts by field value, status, or category)
- Time-bucketed activity → `line` or `area` (events or records over time)

**Workflow:**
1. Run the correct RED sample + execute (or audit) chain for the question.
2. Aggregate metrics **only** from tool results in the current turn.
3. Pick **1–3** widgets that add insight; skip if prose or a table is clearer.
4. Short markdown summary + single ` ```widget ` fence (see system prompt).

**Anti-hallucination:** Every value in widget JSON must trace to tool output. Do not invent counts, field values, or timestamps.
