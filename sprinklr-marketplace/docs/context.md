# Product Context: Sprinklr Developer MarketPlace

## 📍 Current Implementation Status
* **Last Updated:** June 8, 2026
* **Currently Working On:** MCP tool invocation and LLM integration
* **Completed Features:** 
  - ✅ Full-stack chat application (Spring Boot + React)
  - ✅ MongoDB persistence for conversations and messages
  - ✅ SSE streaming for real-time response delivery
  - ✅ Chat history loading on page reload
  - ✅ Conversation persistence via localStorage
  - ✅ CORS configuration for development
  - ✅ Async/non-blocking HTTP operations
  - ✅ Comprehensive logging throughout stack
* **Known Issues/Blockers:** None. All core chat functionality is stable.

---

## 🎯 The Goal
Build a centralized, AI-powered assistant platform with a standalone UI that integrates with internal and external enterprise systems via the Model Context Protocol (MCP). 

## 🔄 The Core Flow
1. A user submits a natural language prompt.
2. The Spring Boot backend fetches the last 15 messages of conversation history from MongoDB.
3. The backend sends the prompt, history, and a whitelisted set of MCP Tool Schemas to an LLM.
4. The LLM decides which tools to call.
5. The backend executes JSON-RPC requests to the respective MCP servers in parallel.
6. The backend feeds the raw tool data back to the LLM.
7. The LLM summarizes the data, and the backend streams the final response to the UI via SSE.

## 🔌 Supported MCP Integrations (Whitelist)
- **JIRA:** Fetch assigned tickets, update status, add comments.
- **GitLab:** Fetch MR status, fetch pipeline status, compare revisions.
- **MS Teams:** Fetch channel alerts, fetch unread notifications.
- **Red (Internal):** Fetch deployment summaries, trigger auto-deployment requests.

## 🗄️ Data Persistence (MongoDB)
- **Users:** Stores AES-256 encrypted JIRA PATs and GitLab tokens.
- **Conversations:** Metadata and session state.
- **Messages:** AI memory. Raw JSON tool results must be truncated after LLM summarization.
- **McpConfigs:** Dynamic registry storing active MCP server URLs and their allowed tools.

---

## 🛠️ Backend Architecture (Spring Boot WebFlux)

### Running the Backend
```bash
cd sprinklr-marketplace
./mvnw clean install
./mvnw spring-boot:run
# Backend runs on http://localhost:8080
```

### Key Components

**ChatController** (`infrastructure/inbound/rest/ChatController.java`)
- `POST /api/v1/chat/stream` - Main streaming endpoint
  - Accepts `ChatRequest` (conversationId, message)
  - Returns SSE stream of assistant responses
  - Non-blocking via `Schedulers.boundedElastic()`
- `GET /api/v1/chat/history?conversationId=...&limit=50` - Fetch conversation history
  - Returns `ChatHistoryResponse` with list of `MessageDto`
  - Loads up to 50 recent messages from MongoDB

**ChatOrchestrator** (`application/service/ChatOrchestrator.java`)
- Core business logic orchestrating the agentic loop
- `streamChat()` - Entry point called by controller
- `runAgenticLoop()` - Manages conversation flow (prompt → LLM → tools → response)
- `handleTextOnlyResponse()` - Streams text responses via Flow.Subscriber
- `handleToolCalls()` - Executes MCP tools and fetches summaries
- Auto-creates conversations if conversationId not found

**WebFluxConfig** (`infrastructure/config/WebFluxConfig.java`)
- CORS enabled for development (localhost:5173, localhost:3000)
- Allows all HTTP methods and credentials

**MongoChatHistoryAdapter** (`infrastructure/outbound/persistence/MongoChatHistoryAdapter.java`)
- Implements `ChatHistoryPort` (hexagonal architecture)
- Methods: `saveConversation()`, `saveMessage()`, `findConversationById()`, `findRecentMessages()`
- All operations async/non-blocking via Spring Data Reactive MongoDB
- Comprehensive logging for debugging

### Data Models

**MessageDto** (`domain/model/MessageDto.java`)
- Lightweight API representation: id, conversationId, role, content, createdAt
- Factory method `fromDomain()` for conversion from domain Message

**ChatHistoryResponse** (`application/dto/ChatHistoryResponse.java`)
- Contains list of MessageDtos for history endpoint
- Factory method `fromMessages()` for batch conversion

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

**App.tsx** (`src/App.tsx`)
- Root component managing global chat state
- Implements conversation ID persistence via localStorage (key: `conversationId`)
- `useEffect` loads conversationId on mount, stores new IDs automatically
- "New Chat" button to start fresh conversations (generates new UUID)
- Passes conversationId to Chat component

**Chat.tsx** (`src/components/Chat.tsx`)
- Main chat UI component
- State: messages[], input, isLoading, error, isLoadingHistory, lastPrompt
- `useEffect` calls `fetchChatHistory()` when conversationId changes
- `handleSendMessage()` → calls `streamChat()` and updates UI with streamed chunks
- Real-time message streaming via SSE with chunk callback
- Retry mechanism for failed messages
- Dynamic textarea resizing with line breaks

**chatService.ts** (`src/services/chatService.ts`)
- HTTP client for chat operations
- `streamChat(chatRequest, onChunk)` - Uses fetchEventSource to stream responses
  - Logs SSE events for debugging
  - Calls `onChunk` callback for each SSE message event
- `fetchChatHistory(conversationId, limit)` - GET /api/v1/chat/history
  - Returns array of MessageDtos
  - Handles error for new conversations gracefully

### Persistence Strategy
- **Client-side:** localStorage stores current `conversationId` (survives page reload)
- **Server-side:** MongoDB stores all conversations and messages with timestamp
- **On Page Load:** Chat.tsx calls `fetchChatHistory()` to load all previous messages

### Data Models

**types/chat.ts**
- `ChatRequest` - { conversationId, message }
- `MessageDto` - { id, conversationId, role, content, createdAt }
- `ChatHistoryResponse` - { messages: MessageDto[] }

---

## 🔐 Important Configuration

### CORS Settings (Development)
Frontend on localhost:5173, backend on localhost:8080. CORS configured in `WebFluxConfig` to allow cross-origin requests.

### React Strict Mode
⚠️ **NOTE:** Removed `<React.StrictMode>` from `main.tsx` to prevent double-invocation of state setters in development. This was causing apparent message duplication in logs (fixed June 8, 2026).

### SSE Streaming
- Uses `@microsoft/fetch-event-source` library on frontend
- Backend emits via `Flow.Subscriber<String>` with Reactor adapters
- Each message event becomes a chunk in frontend

---

## 📋 Critical Changes Log

**June 8, 2026**
- Removed `<React.StrictMode>` from main.tsx (fixed React double-invocation of state setters)
- Added comprehensive SSE logging to chatService.ts
- Added chunk count tracking to Chat.tsx
- Issue resolved: Messages displaying correctly without duplication

**Previous Changes**
- Added CORS configuration (WebFluxConfig.java)
- Implemented GET /api/v1/chat/history endpoint
- Fixed ChatOrchestrator to auto-create conversations
- Switched from in-memory to MongoDB persistence
- Added localStorage for conversation persistence on frontend
- Fixed async/blocking issues with Schedulers.boundedElastic()
- Implemented chat history loading on page reload