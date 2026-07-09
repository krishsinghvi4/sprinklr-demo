# Adding a Custom MCP Server

This guide explains how to add your own MCP server to Sprinklr Chat via the Profile page. Custom MCP configurations are **private to your account** — other users cannot see or use them.

## Compatibility requirements

Custom MCPs must meet these requirements:

- **Transport**: Streamable HTTP (the same protocol used by the built-in MCP client)
- **Authentication**: Credential-based only (API token, bearer token, or custom header)
- **OAuth is not supported** for custom MCPs (OAuth requires server-side client registration)

The built-in MCPs (Jira, GitLab, RED) continue to work unchanged — you do not need to configure them.

## Quick start

1. Open **Profile** in the chat UI
2. Scroll to **Custom MCP Servers**
3. Click **Add custom MCP**
4. Paste and edit the JSON template below
5. Click **Save custom MCP**
6. In **MCP Marketplace** (below on the same page), find your new server and click **Connect**
7. Enter your credentials when prompted

## JSON configuration schema

Paste this into the Profile JSON editor and customize the values:

```json
{
  "displayName": "My Slack MCP",
  "description": "Team Slack MCP via internal bot token",
  "endpointUrl": "https://slack-mcp.internal.company.com/mcp",
  "serverIdPrefix": "myslack",
  "tokenField": "apiToken",
  "headerMode": "BEARER",
  "credentialFields": [
    {
      "key": "apiToken",
      "label": "Bot Token",
      "type": "password",
      "required": true
    }
  ],
  "connectProbe": {
    "tool": "ping",
    "arguments": {},
    "failureMessage": "Invalid bot token"
  },
  "skillText": "## My Slack MCP\nUse `myslack.send_message` to send messages to channels."
}
```

### Field reference

| Field | Required | Description |
|-------|----------|-------------|
| `displayName` | Yes | Human-readable name shown in the MCP Marketplace |
| `description` | No | Short description of what this MCP does |
| `endpointUrl` | Yes | Streamable HTTP MCP endpoint (`http://` or `https://`) |
| `serverIdPrefix` | Yes | Unique prefix for tool names (e.g. `myslack` → `myslack.send_message`). Lowercase letters, numbers, underscores, hyphens. Must be unique among your custom MCPs |
| `tokenField` | Yes | Key in the credential form that holds the access token (usually `apiToken`) |
| `headerMode` | Yes | How the token is sent: `BEARER`, `PRIVATE_TOKEN`, or `CUSTOM` |
| `customHeaderName` | When `CUSTOM` | Header name when using `CUSTOM` header mode |
| `credentialFields` | No | Form fields shown at connect time. Defaults to a single password field for `tokenField` |
| `connectProbe` | No | Optional validation tool invoked at connect time to verify credentials |
| `skillText` | No | Markdown guidance injected into the LLM system prompt when this MCP is connected |

### Header modes

| Mode | HTTP header |
|------|-------------|
| `BEARER` | `Authorization: Bearer <token>` |
| `PRIVATE_TOKEN` | `PRIVATE-TOKEN: <token>` (GitLab-style) |
| `CUSTOM` | `<customHeaderName>: <token>` |

### Connect probe (optional)

If your MCP exposes a lightweight health/ping tool, configure `connectProbe` to validate credentials at connect time:

```json
"connectProbe": {
  "tool": "ping",
  "arguments": {},
  "failureMessage": "Invalid access token — verify the token is valid and not expired."
}
```

If omitted, connect proceeds after the MCP handshake without a live tool call.

## Writing skill guidance (`skillText`)

The `skillText` field is optional markdown that teaches the assistant how to use your MCP. It is injected into the LLM system prompt when your MCP is connected — similar to the built-in `jira.md`, `gitlab.md`, and `red.md` skill files.

Good skill text includes:

- When to use this MCP vs. other tools
- Required workflow steps (e.g. "always list channels before sending a message")
- Tool naming conventions (`<serverIdPrefix>.<tool_name>`)
- Common pitfalls and parameter semantics

Example:

```markdown
## My Slack MCP

Use `myslack.list_channels` before `myslack.send_message` to resolve channel IDs.

- `myslack.send_message` requires `channel_id` (not channel name)
- For thread replies, pass `thread_ts` from the parent message
```

## Connecting after save

After saving a custom MCP config:

1. It appears in **MCP Marketplace** with a **Custom** badge
2. Click **Connect** and enter the credentials defined in `credentialFields`
3. The app discovers tools, validates via `connectProbe` (if configured), and stores the connection
4. Tools become available in chat under the `serverIdPrefix` namespace

The config `id` (returned by the API) is used as `catalogServerId` when connecting — you do not need to set this manually.

## Multi-tenancy

- Custom MCP configs are stored in `user_mcp_configs` scoped by `userId`
- Connections and encrypted credentials are scoped per user
- Skill text is stored inline in your config document (not shared globally)
- Other users cannot see, connect to, or modify your custom MCPs

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/mcp/user-configs` | Create a custom MCP config |
| `GET` | `/api/v1/mcp/user-configs` | List your custom MCP configs |
| `DELETE` | `/api/v1/mcp/user-configs/{id}` | Delete a config (disconnect first) |

Connect via the existing endpoint:

```
POST /api/v1/mcp/connections
{
  "catalogServerId": "<config id from POST response>",
  "credentials": { "apiToken": "your-token" }
}
```

## Known limitations

- **No OAuth**: Custom MCPs support credential auth only
- **No local tool extensions**: Built-in MCPs have server-specific enhancements (e.g. Jira changelog local tool, RED sample query cache). Custom MCPs use discovered remote tools only
- **No cross-workflow skills**: Cross-server workflow skills (e.g. Jira + RED audit investigation) are catalog-defined only
- **One connection per MCP**: You can have at most one active connection per catalog server ID (same as built-in MCPs)
- **Delete requires disconnect**: You must disconnect a custom MCP before deleting its configuration

## Troubleshooting

| Problem | Solution |
|---------|----------|
| "serverIdPrefix already exists" | Choose a different prefix or delete the existing config |
| "Disconnect before deleting" | Disconnect from MCP Marketplace first |
| Connect fails with probe error | Check `connectProbe.tool` name and credentials; remove probe to skip validation |
| Tools not appearing in chat | Verify VPN connectivity to the MCP endpoint; check endpoint URL |
| LLM not using tools correctly | Add or improve `skillText` with workflow guidance |
