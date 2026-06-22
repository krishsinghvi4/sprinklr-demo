#!/usr/bin/env bash
# Copy to export-env.sh, fill in your values, then source before starting the backend:
#   cp export-env.example.sh export-env.sh
#   source export-env.sh
#   ./mvnw spring-boot:run

# --- Mail (OTP signup / password reset) ---
export MAIL_PROVIDER="smtp"
export MAIL_HOST="smtp.gmail.com"
export MAIL_PORT="587"
export MAIL_USERNAME="your@gmail.com"
export MAIL_PASSWORD="your-16-char-app-password"
export MAIL_CONSOLE_OTP_FALLBACK="true"

# --- JWT ---
export JWT_SECRET="w42vQZYrgqGqmsoODPp6KaJ+yQmCWc6PiCm8GY6wbnY="

# --- LLM router (Sprinklr IntuitionX) ---
# Set LLM_STUB=true to skip the real router locally; otherwise set LLM_ROUTER_COOKIE.
export LLM_STUB="false"
export LLM_BASE_URL="http://prod0-intuitionx-llm-router-v2.sprinklr.com"
export LLM_ROUTER_COOKIE="xxxx"
export LLM_CONNECT_TIMEOUT_MS="30000"
export LLM_READ_TIMEOUT_MS="100000"

# --- Chat ---
export CHAT_HISTORY_TURN_LIMIT="5"

# --- MCP marketplace ---
# Generate a stable key with: openssl rand -base64 32
export MCP_ENCRYPTION_KEY="xxxx"
export MCP_CONNECT_TIMEOUT_MS="10000"
export MCP_READ_TIMEOUT_MS="60000"
export MCP_MAX_RESPONSE_BYTES="5242880"
export MCP_MAX_AGENTIC_ITERATIONS="5"
export MCP_MAX_TOOL_CALLS_PER_TURN="10"
export MCP_OAUTH_REDIRECT_URI="http://localhost:5173/oauth/callback"
export MCP_OAUTH_SUCCESS_REDIRECT_URL="http://localhost:5173/profile?oauth=success"
export MCP_OAUTH_ERROR_REDIRECT_URL="http://localhost:5173/profile?oauth=error"

# --- GitLab MCP (zereight/docker on localhost:3333) ---
export GITLAB_MCP_SERVER_URL="http://127.0.0.1:3333/mcp"
# private-token (default) or bearer — must match how the MCP server expects PAT headers
export GITLAB_MCP_AUTH_HEADER="private-token"

# --- Mail via Microsoft Graph (only if MAIL_PROVIDER=graph) ---
# export AZURE_TENANT_ID="xxxx"
# export AZURE_CLIENT_ID="xxxx"
# export AZURE_CLIENT_SECRET="xxxx"
