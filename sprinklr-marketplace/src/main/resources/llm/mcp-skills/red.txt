## RED MCP guidance

### Authentication
- All RED tools require a valid RED access token forwarded as `Authorization: Bearer <token>`.
- Use `red_ping` only when troubleshooting connectivity — not as a default first step for every request.

### Workflow
- Call the tool that best matches the user's request using the IDs and parameters they provide (partner ID, client ID, environment, index/collection, etc.).
- Do not proactively call lookup or search tools to resolve IDs the user already gave or is expected to know.
- If required parameters are missing from the user's message, respond with text only and ask for them before calling a tool.
- If a tool returns an error, explain it in plain language and ask for corrected input.

**Exception — schema discovery before filtered queries:** For Elasticsearch and Mongo query requests, you MUST call the corresponding sample tool first in the same turn before the execute tool. This is required discovery, not optional lookup.

### Elasticsearch queries (mandatory two-step)

**Step 1 — Sample (always first):**
- Call `red_sample_elasticsearch_query` with scope args from the user: `username`, `partnerId`, `serverType`, `searchType`, `indexName`, and optional `env`, `preference`, `host`, `queryExecutorLongQuery`.
- Do **not** pass a `query` parameter — the tool returns up to 5 sample documents with an internal empty query (size 5).
- Read field names from the returned documents. **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_elastic_search_query` with the same scope args plus a `query` filter.
- Use **only field names seen in the sample results**.
- Use **only filter values the user explicitly provided** in their message.
- If the user asked for LinkedIn, Facebook, or any channel/platform filter, build the execute query using the **exact dotted path** from the sample (e.g. `audience.channelTypes`), not a shortened or guessed name.
- For array fields such as `audience.channelTypes`, use a **`terms`** query with a JSON array value (e.g. `["FACEBOOK"]`), not `term`.
- **Never append `.keyword`** to a field name unless that exact path (including `.keyword`) appears in the sample `_source` or the appended FILTER FIELD PATHS list.
- Sample output may include a `FILTER FIELD PATHS` section — copy those paths exactly into the execute query.

**Elasticsearch `query` format:** The `query` argument is a **JSON string** (escaped), not a nested object:

```json
{
  "partnerId": 190,
  "serverType": "AUDIT_LOG",
  "searchType": "SEARCH",
  "indexName": "audit_log_p190*",
  "query": "{\"query\":{\"bool\":{\"must\":[{\"term\":{\"assetId\":\"6xxxxxxxxxxx6e072\"}}]}}}"
  note that this query is just an example , you are not to use these values if user input isnt sufficient .
}
```

Optional top-level args: `username`, `env`, `preference`, `host`, `queryExecutorLongQuery`.

### Mongo queries (mandatory two-step)

**Step 1 — Sample (always first):**
- Call `red_sample_mongo_query` with scope args from the user: `partnerId`, `serverType`, `collectionName`, `queryType`, and optional `sequenceName`, `skip`, `includeFields`, `sortField`, `sortDirection`, `env`.
- Do **not** pass a `query` or `limit` parameter — the tool uses an internal empty filter `{}` and returns up to 5 documents.
- Read field names from the returned documents. **Do not treat sample rows as the user's answer** — they are unfiltered schema probes.

**Step 2 — Filtered query (required when the user asks to fetch, search, or filter records):**
- Call `red_execute_mongo_query` with the same scope args plus `query`, `limit`, and other execute parameters.
- Use **only field names seen in the sample results**.
- Use **only filter values the user explicitly provided** in their message.

### Common operations
- **Health / connectivity:** `red_ping`
- **Schema sample (before filtered queries):** `red_sample_elasticsearch_query`, `red_sample_mongo_query`
- **Filtered query execution:** `red_execute_elastic_search_query`, `red_execute_mongo_query`
- **Other reads:** tools under `red_get_*` — check tool descriptions for required parameters
- **Sprinklr operations:** client lookup, ES stats, and related tools when the user asks for those capabilities

### Parameter rules
- Never invent IDs, client names, environment values, field names, or filter values the user did not provide.
- Field names for filters must come from sample tool results in the current turn — do not guess schema.
- Prefer the most specific tool for the request; do not broaden to unrelated lookup tools when the user already supplied the needed identifiers.
