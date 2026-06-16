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

### Key Constraint
**No hardcoded server whitelist.** JIRA, GitLab, MS Teams, and Red are reference integrations, not compile-time enums. The system prompt may list examples, but the runtime tool set is always driven by the user's registered servers.

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
