# Product Context: Sprinklr Developer MarketPlace

## 📍 Current Implementation Status
* **Last Updated:** June 18, 2026
* **Currently Working On:** Additional MCP server catalog entries (MS Teams, etc.)
* **Completed Features:**
  - ✅ Full-stack chat application (Spring Boot + React)
  - ✅ MongoDB persistence for conversations and messages
  - ✅ SSE streaming for real-time response delivery
  - ✅ Chat history loading on page reload
  - ✅ Conversation persistence via localStorage + server-side user-scoped storage
  - ✅ CORS configuration for development
  - ✅ Async/non-blocking HTTP operations
  - ✅ Comprehensive logging throughout stack
  - ✅ LLM summary responses saved to MongoDB (FIXED June 8)
  - ✅ User authentication (register, email OTP verification, login, forgot/reset password)
  - ✅ JWT-based stateless auth with Spring Security (`/api/**` protected)
  - ✅ OTP email delivery (Gmail SMTP default; Microsoft Graph optional)
  - ✅ User-scoped conversations (ownership enforced on chat/history endpoints)
  - ✅ Chat dashboard with conversation list (`GET /api/v1/chat/conversations`)
  - ✅ LLM integration layer (Sprinklr IntuitionX router via WebClient + Resilience4j circuit breaker)
  - ✅ Stub LLM adapter for local dev (`app.llm.stub-enabled=true`)
  - ✅ System prompt for Sprinklr Developer Marketplace copilot (`classpath:llm/system-prompt.txt`)
  - ✅ **MCP Marketplace** — extensible catalog-driven server registration (`mcp-catalog.json`)
  - ✅ **Profile API** — `GET /api/v1/profile` (user info + marketplace state)
  - ✅ **MCP Connections API** — `POST /api/v1/mcp/connections`, `DELETE /api/v1/mcp/connections/{id}`
  - ✅ **Atlassian Jira MCP** — OAuth redirect connect (PKCE + DCR) via catalog entry `atlassian-jira`
  - ✅ **MCP extensibility refactor** — provider/auth abstraction (`McpProvider`, `AuthFlowRouter`, catalog-driven `connectMethod`)
  - ✅ **GitLab catalog example** — credential-form connect (`BASIC_EMAIL_TOKEN`) demonstrating multi-auth marketplace
  - ✅ **AES-256-GCM credential vault** — encrypted storage in `mcp_connections` collection
  - ✅ **Streamable HTTP MCP client** — initialize, tools/list, tools/call via WebClient
  - ✅ **Per-connection circuit breakers** — Resilience4j keyed by `mcp-{connectionId}`
  - ✅ **ChatOrchestrator agentic loop** — sequential tool execution (`concatMap`), iteration + tool-call limits
  - ✅ **Turn-based LLM context** — last N user prompts (`app.chat.history-turn-limit`); prior turns conversational, current turn full agentic; TOOL JSON truncated after summary
  - ✅ **User tool schemas loaded into LLM** — from active MCP connections per user
  - ✅ **Progressive tool context** — lightweight LLM router selects relevant tools (or zero for conversational prompts), deterministic expander adds prerequisites from LLM-generated per-server dependency graphs (list-based edges on `mcp_connections`), agent LLM receives only the capped scoped set; cross-turn continuation only for incomplete workflows via `pending_workflows` (`app.mcp.tool-selection.*`)
  - ✅ **SprinklrLlmRouterAdapter.streamSummary()** — wired via LlmService
  - ✅ **Profile + Marketplace UI** — `/profile` page with OAuth redirect + credential modal connect flows
* **In Progress / Next Up:**
  - 🔲 Add more catalog entries (MS Teams, Red)
  - 🔲 Re-sync tools on `tools/list_changed` notification
  - 🔲 True LLM streaming for streamSummary (currently single-chunk delivery)
* **Known Issues/Blockers:**
  - `MCP_ENCRYPTION_KEY` should be set in production (defaults to ephemeral key in dev)
  - GitLab catalog entry uses a placeholder endpoint — replace with real MCP URL before production use

---

## 🎯 The Goal
Build a centralized, AI-powered assistant platform with a standalone UI that integrates with internal and external enterprise systems via the Model Context Protocol (MCP).

**Primary product feature:** An **extensible MCP Marketplace** where users register MCP servers by providing API credentials, and those servers' tools become immediately available in chat — no backend code changes required to add a new integration.

## 🏪 MCP Marketplace Vision

### Extensibility Principle
Adding a new MCP server must be a **configuration + credentials** operation, not a code change:
1. User opens Marketplace UI → "Add MCP Server"
2. User enters server URL and credential fields (PAT, API key, OAuth token, etc.)
3. Backend runs MCP `initialize` + `tools/list` to discover available tools
4. Tools are stored in `McpConfigs` and injected into the LLM context for that user
5. User can immediately chat and invoke those tools

### Reference Integrations (not hardcoded — examples only)
- **JIRA:** Fetch assigned tickets, update status, add comments.
- **GitLab:** Fetch MR status, fetch pipeline status, compare revisions.
- **MS Teams:** Fetch channel alerts, fetch unread notifications.
- **Red (Internal):** Fetch deployment summaries, trigger auto-deployment requests.

## 🔄 The Core Flow
1. A user submits a natural language prompt.
2. The Spring Boot backend fetches recent conversation history from MongoDB (last N user turns via `app.chat.history-turn-limit`, default 5).
3. The backend loads the user's registered MCP tool schemas from `McpConfigs`.
4. The backend sends the prompt, history, and active tool schemas to the LLM.
5. The LLM decides which tools to call (if any).
6. The backend executes JSON-RPC requests to the respective MCP servers **sequentially** in LLM-requested order (one tool may depend on a prior result).
7. Tool results are appended to history; the LLM is called again to summarize or request more tools.
8. The backend streams the final response to the UI via SSE.

## 🗄️ Data Persistence (MongoDB)

| Collection | Purpose |
|-----------|---------|
| **Users** | Auth credentials (bcrypt password), email verification status. Will store AES-256 encrypted MCP API tokens/PATs per server. |
| **Otps** | Email OTP codes for signup and password reset flows |
| **Conversations** | Metadata, user ownership, session state |
| **Messages** | AI memory (USER, ASSISTANT, TOOL roles). Raw TOOL JSON truncated to a stub after LLM summarization (`truncateToolResults`). |
| **McpConnections** | Per-user MCP server registry (`mcp_connections`): catalog server ID, encrypted credentials, MCP session ID, discovered tool schemas, status, and the LLM-generated `toolDependencyGraph` + `dependencyGraphStatus` for that server |
| **pending_workflows** | Cross-turn tool-workflow continuation for **incomplete** multi-step flows (`awaitingGoalTools`, satisfied tools, summaries); TTL-indexed on `expiresAt` |
| **mcp_oauth_states** | Ephemeral OAuth CSRF/PKCE state (`state`, `userId`, `catalogServerId`, `providerKey`, `codeVerifier`); TTL auto-expires; deleted on callback |
| **mcp_dcr_clients** | Dynamic OAuth client registrations (`providerKey`, `redirectUri`, `clientId`, `clientSecret`) scoped per OAuth issuer |

---

## 🛠️ Backend Architecture (Spring Boot)

### Running the Backend
```bash
cd sprinklr-marketplace
./mvnw clean install
./mvnw spring-boot:run
# Backend runs on http://localhost:8080
```

### Key Components

**AuthController** (`infrastructure/inbound/rest/AuthController.java`)
- `POST /api/auth/register` — create account, queue signup OTP
- `POST /api/auth/verify-signup-otp` — verify email
- `POST /api/auth/login` — returns JWT
- `POST /api/auth/forgot-password` / `verify-forgot-otp` / `reset-password`

**ChatController** (`infrastructure/inbound/rest/ChatController.java`)
- `POST /api/v1/chat/stream` — Main streaming endpoint (JWT required)
- `GET /api/v1/chat/history?conversationId=...&limit=50` — Fetch conversation history
- `GET /api/v1/chat/conversations` — List user's conversations for dashboard

**ChatOrchestrator** (`application/service/ChatOrchestrator.java`)
- Core business logic orchestrating the agentic loop
- `streamChat()` → `runAgenticLoop()` → LLM complete → tool calls → streamSummary
- `handleToolCalls()` — executes MCP tools (currently parallel via `flatMap`; **must migrate to sequential `concatMap`**)
- `createCapturingSummarySubscriber()` — captures streamed summary and persists to MongoDB

**LlmService** (`infrastructure/outbound/llm/LlmService.java`)
- Internal LLM orchestration: request assembly, WebClient HTTP call, response parsing
- Resilience4j circuit breaker (`llmRouter` instance)
- Stub vs real router switched via `app.llm.stub-enabled` in `LlmConfig`

**HttpMcpClientAdapter** (`infrastructure/outbound/mcp/HttpMcpClientAdapter.java`)
- Implements `McpServerPort` — currently returns simulated JSON
- **Next:** WebClient JSON-RPC to real MCP server URLs from `McpConfigs`

**SecurityConfig** (`infrastructure/config/SecurityConfig.java`)
- Stateless JWT auth; `/api/auth/**` public; all other `/api/**` authenticated

### MCP Domain Models (scaffolded)
- `McpTool` — tool name, description, serverId, inputSchemaJson
- `McpInvocation` — serverId, toolName, argumentsJson
- `McpInvocationResult` — toolCallId, success, content/errorMessage
- `McpServerPort` — `invoke(McpInvocation)` outbound port

### Data Models
- `MessageDto` — lightweight API representation for history endpoint
- `ChatHistoryResponse` — batch of MessageDtos
- `ConversationSummaryDto` — id, preview, createdAt, updatedAt

---

## 🎨 Frontend Architecture (React + Vite + TypeScript)

### Running the Frontend
```bash
cd sprinklr-chat-ui
npm install
npm run dev
# Frontend runs on http://localhost:5173
```

### Key Components

**App.tsx** — React Router with auth-gated routes
- `/login`, `/signup`, `/forgot-password` — public
- `/` — ChatDashboardPage (conversation list)
- `/chat/:conversationId` — ChatPage

**AuthContext** (`src/context/AuthContext.tsx`) — JWT token management, login/logout state

**ChatDashboardPage** — lists user conversations, "New Chat" button

**Chat.tsx** — main chat UI with SSE streaming, history loading, retry

**chatService.ts** — `streamChat()`, `fetchChatHistory()`, `fetchConversations()`

### Persistence Strategy
- **Client-side:** JWT in localStorage; `conversationId` persisted per session
- **Server-side:** MongoDB stores all conversations and messages, scoped to authenticated user

---

## 🔐 Important Configuration

### Chat
```
app.chat.history-turn-limit=${CHAT_HISTORY_TURN_LIMIT:5}   # user prompts included in LLM context
```

### LLM Router (`application.properties`)
```
app.llm.stub-enabled=${LLM_STUB:false}          # true = StubLlmAdapter (local dev)
app.llm.base-url=${LLM_BASE_URL:...}            # Sprinklr IntuitionX router
app.llm.cookie=${LLM_ROUTER_COOKIE:}            # Required when stub=false
app.llm.system-prompt-path=classpath:llm/system-prompt.txt
app.llm.tool-router-prompt-path=classpath:llm/tool-router-prompt.txt
app.llm.tool-dependency-graph-prompt-path=classpath:llm/tool-dependency-graph-prompt.txt
```

### Progressive Tool Context (`application.properties`)
```
app.mcp.tool-selection.enabled=true                 # false = legacy (send all tools)
app.mcp.tool-selection.max-tools=15                 # hard cap of full-schema tools per turn
app.mcp.tool-selection.router-max-primary-tools=8   # soft cap on router picks
app.mcp.tool-selection.router-history-turns=2       # recent turns given to the router
app.mcp.tool-selection.generate-graph-on-connect=true
app.mcp.tool-selection.dependency-preflight-enabled=false  # opt-in strict ordering gate
app.mcp.tool-selection.dependency-preflight-enabled=true   # runtime ordering safety net (default on)
app.mcp.tool-selection.continuation-ttl-hours=24
```

### Auth
```
app.jwt.secret=${JWT_SECRET:...}
app.jwt.expiration-ms=86400000
```

### Mail (OTP)
```
app.mail.provider=${MAIL_PROVIDER:smtp}         # smtp | graph
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
```

### CORS
Frontend on localhost:5173, backend on localhost:8080. Configured in `SecurityConfig` and `WebFluxConfig`.

---

## 📋 Critical Changes Log

**June 18, 2026 (MCP Extensibility Refactor)**
- **IMPLEMENTED:** Central MCP provider abstraction (`McpProvider`, `AbstractMcpProvider`, `McpProviderResolver`)
- **IMPLEMENTED:** Auth flow routing — `OAUTH_REDIRECT` vs `CREDENTIAL_FORM` via catalog `connectMethod`
- **IMPLEMENTED:** Generalized OAuth client/token model (`McpOAuthToken`) with catalog-driven OAuth config
- **IMPLEMENTED:** Multi-provider DCR/state persistence (`providerKey` on `mcp_dcr_clients` / `mcp_oauth_states`)
- **IMPLEMENTED:** OAuth state cleanup on callback + stale state purge before new OAuth start
- **IMPLEMENTED:** API error codes for UI (`errorCode` on `MessageResponse`)
- **IMPLEMENTED:** Frontend connect UX driven by backend `connectMethod` (no hardcoded Jira branch)

**June 15, 2026 (MCP Marketplace)**
- **IMPLEMENTED:** Extensible MCP Marketplace with catalog-driven server registration
- **IMPLEMENTED:** Profile API (`GET /api/v1/profile`) + MCP connection endpoints
- **IMPLEMENTED:** Atlassian Jira as first catalog entry (Basic email+API token auth)
- **IMPLEMENTED:** Streamable HTTP MCP client, AES credential vault, per-connection circuit breakers
- **IMPLEMENTED:** ChatOrchestrator sequential tool execution + agentic loop limits
- **IMPLEMENTED:** Profile/Marketplace UI at `/profile`

**June 15, 2026 (Planning)**
- **PLANNED:** MCP Marketplace as primary feature — extensible server registration via user credentials
- **ARCHITECTURE DECISION:** Tool execution changed from parallel → **sequential** (tool calls may depend on prior results)
- **DOCUMENTED:** Full implementation status update — auth, LLM layer, MCP scaffolding, remaining work items

**June 8, 2026**
- **FIXED:** LLM summary responses not being saved to MongoDB after tool invocation
  - Added `createCapturingSummarySubscriber()` in ChatOrchestrator
- Removed `<React.StrictMode>` from main.tsx (fixed React double-invocation)
- Added comprehensive SSE logging

**Earlier**
- Added CORS configuration, chat history endpoint, MongoDB persistence
- Implemented localStorage conversation persistence on frontend
- Fixed async/blocking issues with Schedulers.boundedElastic()
