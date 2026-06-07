# Product Context: Sprinklr Developer MarketPlace

## 📍 Current Implementation Status
* **Last Updated:** [Leave Blank - Cursor will update this]
* **Currently Working On:** Project Initialization and setting up Hexagonal boilerplate.
* **Completed Features:** N/A
* **Known Issues/Blockers:** N/A

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