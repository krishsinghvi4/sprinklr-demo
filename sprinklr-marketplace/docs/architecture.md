# Technical Architecture & Constraints

## 1. Hexagonal Structure
- `domain/`: Pure Java. No dependencies on external frameworks.
- `application/`: Contains the Orchestrator logic.
- `infrastructure/`: Adapters for MongoDB, LLM APIs, and MCP JSON-RPC calls over HTTP.

## 2. Low-Latency Execution
- **Parallel Execution:** Multiple `tool_use` requests from the LLM MUST be executed in parallel using `Flux.merge()` or `Mono.zip()`. 
- **Streaming:** Endpoints returning LLM responses MUST use Server-Sent Events (SSE) returning a `Flux<String>`.

## 3. Resilience
- **Timeouts:** All WebClient calls require explicit connection/read timeouts (e.g., 5 seconds).
- **Circuit Breakers:** `Resilience4j` must wrap external calls to prevent cascading failures.