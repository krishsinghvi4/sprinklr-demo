## Microsoft Teams Messages guidance

### When to use
Use `teams.search_channel_messages` when the user asks about:
- Teams channel conversations or activity
- What someone said in a Teams channel
- Messages mentioning a person in Teams
- Recent updates or discussions in a specific Teams channel or team

Do **not** use this tool for live Teams API actions (posting messages, creating channels, etc.) — only for searching messages already ingested via the user's Power Automate workflows.

### Tool: `teams.search_channel_messages`

Search the user's indexed Teams channel messages (scoped automatically to their account).

**Parameters** (all optional except you should always pass meaningful filters when the user names them):
| Parameter | Use when |
|-----------|----------|
| `query` | User wants full-text search in message bodies |
| `channelName` | User names a channel (e.g. "incidents", "general") |
| `teamName` | User names a team |
| `mentionedUser` | User asks who was mentioned or messages mentioning someone |
| `since` / `until` | User gives a time window ("last week", "since Monday") — use ISO-8601 |
| `limit` | Default 20, max 50 |

### Mapping natural language
- "What was said in the incidents channel last week?" → `channelName: incidents`, `since: <7 days ago ISO>`
- "Find Teams messages mentioning Vaibhav" → `mentionedUser: Vaibhav`
- "Search Teams for prod outage" → `query: prod outage`

### Response format
Returns JSON with `total` and `messages[]` containing:
- `senderDisplayName`, `channelName`, `teamName`, `createdAt`, `bodyPlainText`, `subject`, `mentions`

Summarize hits in plain language. Quote message text when the user asked for specifics. If no results, say so and suggest checking that Power Automate workflows are posting to the webhook URL from Profile.

### Setup reminder
If the user has not connected Microsoft Teams Messages or has no indexed data, tell them to connect from Profile, copy the webhook URL into each Teams Power Automate workflow, and wait for new messages to arrive.
