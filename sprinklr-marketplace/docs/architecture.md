# Technical Architecture & Constraints

## 1. Hexagonal Structure
- `domain/`: Pure Java. No dependencies on external frameworks. Contains MCP marketplace domain models (`McpTool`, `McpInvocation`, `McpServerConfig`) and ports.
- `application/`: Orchestrator logic (`ChatOrchestrator`), MCP marketplace use cases (server registration, credential management, tool discovery).
- `infrastructure/`: Adapters for MongoDB, LLM APIs, and MCP JSON-RPC calls over HTTP/WebClient.

## 2. MCP Marketplace — Extensibility Design

The marketplace must allow users to connect arbitrary MCP servers without backend code changes.

### Registration Flow
1. User submits server connection details (server URL, transport type, credential fields).
2. Backend validates connectivity via MCP `initialize` + `tools/list` handshake.
3. Discovered tool schemas are persisted in `McpConfigs` (per-user, per-server).
4. On each chat turn, the orchestrator loads the user's active server tool schemas and passes them to the LLM.

### Credential Model
- Credentials are stored encrypted (AES-256) in the `Users` or `McpConfigs` collection.
- Each MCP server adapter reads credentials at invocation time — never embedded in tool schemas sent to the LLM.
- Adding a new server type = new entry in the registry, not a new Java class (generic `HttpMcpClientAdapter` handles JSON-RPC for all HTTP-based MCP servers).

### Ports
| Port | Responsibility |
|------|---------------|
| `McpServerPort` | Execute a single `tools/call` JSON-RPC request against a registered server |
| `McpRegistryPort` | CRUD for user MCP server configs; fetch active tool schemas |
| `McpDiscoveryPort` | `tools/list` handshake to discover tools from a new server URL |
| `CredentialVaultPort` | Encrypt/decrypt user API tokens and PATs at rest |
| `ToolDependencyGraphPort` | Generate a per-server tool dependency graph at connect time (LLM-backed) |
| `ToolRouterPort` | Select the primary tools relevant to a chat turn (lightweight LLM) |
| `PendingWorkflowPort` | Persist cross-turn continuation state for multi-step tool workflows |

### Key Constraint
**No hardcoded server whitelist.** JIRA, GitLab, MS Teams, and Red are reference integrations, not compile-time enums. The system prompt may list examples, but the runtime tool set is always driven by the user's registered servers.

### Provider & Auth Abstraction (June 2026)

Adding MCP servers is **catalog-first**: extend `mcp-catalog.json` with endpoint, `authType`, `connectMethod`, and optional `auth` block. No new Java adapter is required for standard HTTP MCP servers.

| Component | Responsibility |
|-----------|---------------|
| `McpProvider` / `AbstractMcpProvider` | Central connect contract — validate credentials, build auth headers |
| `McpProviderResolver` | Picks specialized provider (e.g. `AtlassianMcpProvider`) or `DefaultCatalogMcpProvider` |
| `AuthFlowRouter` | Routes to `OAuthAuthFlowHandler` or `CredentialAuthFlowHandler` based on `connectMethod` |
| `McpConnectionOrchestrator` | Shared discovery handshake + encrypted persistence (used by OAuth callback and credential connect) |
| `McpAuthStrategy` + registry | Wire-format auth headers keyed by `authType` (`OAUTH_ATLASSIAN`, `BASIC_EMAIL_TOKEN`, …) |
| `McpOAuthTokenRefreshService` | Runtime OAuth refresh for any catalog OAuth entry |

**Connect methods:**
- `OAUTH_REDIRECT` — PKCE OAuth start/callback; UI redirects to authorization URL
- `CREDENTIAL_FORM` — UI shows dynamic `credentialFields`; POST `/api/v1/mcp/connections`

**Tool naming (unchanged):** Discovered tools are exposed to the LLM as `{serverIdPrefix}.{toolName}` (e.g. `jira.search_issues`). `ChatOrchestrator` resolves connections by prefix — this contract is preserved across the refactor.

**OAuth persistence:**
- `mcp_oauth_states` — short-lived CSRF + PKCE verifier; deleted on callback; TTL index for orphans
- `mcp_dcr_clients` — DCR client credentials keyed by `(providerKey, redirectUri)` for multi-issuer support

### Progressive Tool Context — Router + LLM-generated Dependency Graphs (June 2026)

To stay accurate and cheap as the catalog grows toward hundreds of tools, the backend no longer sends every active tool to the agent LLM each turn. Three LLM roles are involved (two at chat time):

| When | Role | Input | Output |
|------|------|-------|--------|
| MCP connect (once per connection) | Dependency graph builder | That server's discovered tools + optional MCP skill guidance (`mcp-skills/*.txt`) | Per-server dependency map (DAG), stored as list edges |
| Chat turn start | Tool router | Compact catalog of the user's connected tools (names + descriptions only) | A few primary tool names |
| Chat agentic loop | Agent (existing) | The scoped tools (router picks + expanded prerequisites) with full schemas | tool_calls or text |

Between the router and the agent, a deterministic expander (no LLM) walks the stored graph to add prerequisite tools in topological order, then caps the set (default 15). Tool execution stays sequential.

**Where dependency graphs live (multi-tenant):** Each graph is stored on the user's own `mcp_connections` document (`toolDependencyGraph` as a list of `{tool, prerequisites}` edges + `dependencyGraphStatus`). Dotted tool names (e.g. `gitlab.list_pipelines`) cannot be MongoDB map keys, so edges use a list structure. Graphs are per-user and per-server and deleted with the connection.

| Component | Responsibility |
|-----------|---------------|
| `ToolDependencyGraphPort` / `LlmToolDependencyGraphGenerator` | Generate + validate (DAG, known tools) the graph at connect; never throws — returns `FAILED` on persistent error |
| `ToolRouterPort` / `LlmToolRouterAdapter` | Cheap text-only LLM call selecting primary tools |
| `ToolSelectionService` (application) | Orchestrates router + deterministic expander + continuation; caps the set |
| `PendingWorkflowPort` / `MongoPendingWorkflowAdapter` | Cross-turn continuation state (`pending_workflows`, TTL-indexed) |
| `ToolDependencyPreflightGuard` | Generic safety net blocking out-of-order calls using the stored graph (default on) |

**Prompts (separate files):** `classpath:llm/tool-dependency-graph-prompt.txt` (connect-time) and `classpath:llm/tool-router-prompt.txt` (chat-time), distinct from `system-prompt.txt`.

**Graceful degradation:** the feature is behind `app.mcp.tool-selection.enabled` (default true). If the router **fails**, selection falls back to all tools. If the router returns **no tools needed**, the agent gets zero tools. If a graph is `FAILED`, expansion is skipped but router-scoped selection still applies. Reconnect MCP servers after deploy to regenerate graphs with the fixed storage format.

## 3. Tool Execution — Sequential Only

Multiple `tool_use` requests from the LLM MUST be executed **sequentially in LLM-requested order**, not in parallel.

**Why:** Tool calls are often dependent — e.g., `jira.fetch_ticket` returns an ID that `jira.add_comment` needs. Parallel execution breaks this chain.

**Implementation pattern:**
```
Flux.fromIterable(toolCalls)
    .concatMap(toolCall -> Mono.fromCallable(() -> mcpServerPort.invoke(toInvocation(toolCall)))
        .subscribeOn(Schedulers.boundedElastic()))
    .collectList()
```

- Use `concatMap`, never `flatMap` or `Flux.merge()` / `Mono.zip()` for tool invocation.
- After all tools in the current batch complete, feed results to the LLM and re-evaluate (agentic loop).
- If the LLM requests more tools, execute the next batch sequentially.

## 4. Agentic Loop
```
User prompt
  → LLM complete (with user's active tool schemas)
  → [tool calls?]
      → execute tools sequentially
      → append tool results to history
      → LLM streamSummary (or re-complete if multi-turn loop)
  → stream final text response via SSE
```

## 5. Low-Latency & Streaming
- **Streaming:** Endpoints returning LLM responses MUST use Server-Sent Events (SSE).
- **Non-blocking I/O:** MCP and LLM outbound calls use WebClient + Reactor. Blocking is confined to `Schedulers.boundedElastic()` at orchestration boundaries.

## 6. Resilience
- **Timeouts:** All WebClient calls require explicit connection/read timeouts (e.g., 5s connect, 30s read for MCP; configurable per server).
- **Circuit Breakers:** Resilience4j wraps external calls (LLM router, MCP servers) to prevent cascading failures.
- **Per-server circuit breakers:** Each registered MCP server gets its own circuit breaker instance keyed by `serverId`.
