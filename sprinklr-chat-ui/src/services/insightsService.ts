import { fetchEventSource } from '@microsoft/fetch-event-source'
import type { WidgetSpec } from '../types/widgets'

const INSIGHTS_BASE = '/api/v1/insights'

function getAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  const token = localStorage.getItem('chat_token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  return headers
}

export interface DashboardConversationSummary {
  id: string
  sourceConversationId: string
  title: string
  preview: string
  turnCount: number
  createdAt: string
  updatedAt: string
}

export interface DashboardTurnSummary {
  id: string
  prompt: string
  version: number
  createdAt: string
}

export interface DashboardConversationDetail extends DashboardConversationSummary {
  turns: DashboardTurnSummary[]
}

export interface DashboardTurnDetail {
  id: string
  dashboardConversationId: string
  sourceChatMessageId: string | null
  prompt: string
  narrative: string
  assistantContent: string
  widgets: WidgetSpec[]
  toolResultSnapshot: string | null
  extendedInsights: Record<string, string>
  version: number
  previousVersionId: string | null
  createdAt: string
}

export interface SaveTurnRequest {
  sourceConversationId: string
  sourceChatMessageId: string
  prompt: string
  assistantContent: string
  widgets: WidgetSpec[]
  toolResultSnapshot?: string
}

export interface SavedMessagesInfo {
  dashboardConversationId: string | null
  savedMessageIds: string[]
}

export async function fetchSavedMessageIds(sourceConversationId: string): Promise<SavedMessagesInfo> {
  const response = await fetch(
    `${INSIGHTS_BASE}/saved-messages?sourceConversationId=${encodeURIComponent(sourceConversationId)}`,
    { headers: getAuthHeaders() },
  )
  if (!response.ok) {
    return { dashboardConversationId: null, savedMessageIds: [] }
  }
  const data = await response.json()
  return {
    dashboardConversationId: data.dashboardConversationId ?? null,
    savedMessageIds: data.savedMessageIds ?? [],
  }
}

export async function fetchDashboardConversations(): Promise<DashboardConversationSummary[]> {
  const response = await fetch(`${INSIGHTS_BASE}/conversations`, {
    headers: getAuthHeaders(),
  })
  if (!response.ok) {
    return []
  }
  const data = await response.json()
  return data.conversations ?? []
}

export async function fetchDashboardConversation(id: string): Promise<DashboardConversationDetail | null> {
  const response = await fetch(`${INSIGHTS_BASE}/conversations/${encodeURIComponent(id)}`, {
    headers: getAuthHeaders(),
  })
  if (!response.ok) {
    return null
  }
  return response.json()
}

export async function fetchDashboardTurn(
  conversationId: string,
  turnId: string,
): Promise<DashboardTurnDetail | null> {
  const response = await fetch(
    `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(turnId)}`,
    { headers: getAuthHeaders() },
  )
  if (!response.ok) {
    return null
  }
  return response.json()
}

export async function saveTurnToDashboard(
  request: SaveTurnRequest,
): Promise<{ dashboardConversationId: string; turnId: string; alreadySaved: boolean } | null> {
  const response = await fetch(`${INSIGHTS_BASE}/turns`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify(request),
  })
  if (!response.ok) {
    return null
  }
  const data = await response.json()
  return {
    dashboardConversationId: data.dashboardConversationId,
    turnId: data.turnId,
    alreadySaved: data.alreadySaved ?? false,
  }
}

export async function updateTurnPrompt(
  conversationId: string,
  turnId: string,
  prompt: string,
): Promise<DashboardTurnDetail | null> {
  const response = await fetch(
    `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(turnId)}`,
    {
      method: 'PUT',
      headers: getAuthHeaders(),
      body: JSON.stringify({ prompt }),
    },
  )
  if (!response.ok) {
    return null
  }
  return response.json()
}

export async function deleteDashboardTurn(conversationId: string, turnId: string): Promise<boolean> {
  const response = await fetch(
    `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(turnId)}`,
    { method: 'DELETE', headers: getAuthHeaders() },
  )
  return response.ok
}

export async function deleteDashboardConversation(conversationId: string): Promise<boolean> {
  const response = await fetch(
    `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}`,
    { method: 'DELETE', headers: getAuthHeaders() },
  )
  return response.ok
}

export async function expandWidgetInChat(
  widget: WidgetSpec,
  toolResultSnapshot?: string,
  userFocus?: string,
): Promise<string | null> {
  const response = await fetch(`${INSIGHTS_BASE}/expand`, {
    method: 'POST',
    headers: getAuthHeaders(),
    body: JSON.stringify({ widget, toolResultSnapshot, userFocus }),
  })
  if (!response.ok) {
    return null
  }
  const data = await response.json()
  return data.markdown ?? null
}

export async function expandDashboardWidget(
  conversationId: string,
  turnId: string,
  widgetId: string,
  toolResultSnapshot?: string,
): Promise<string | null> {
  const response = await fetch(
    `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(turnId)}/widgets/${encodeURIComponent(widgetId)}/expand`,
    {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ toolResultSnapshot }),
    },
  )
  if (!response.ok) {
    return null
  }
  const data = await response.json()
  return data.markdown ?? null
}

export async function streamRegenerateTurn(
  conversationId: string,
  turnId: string,
  onChunk: (chunk: string) => void,
  onComplete: () => void,
  onError: (error: Error) => void,
  signal?: AbortSignal,
): Promise<void> {
  try {
    await fetchEventSource(
      `${INSIGHTS_BASE}/conversations/${encodeURIComponent(conversationId)}/turns/${encodeURIComponent(turnId)}/regenerate`,
      {
        method: 'POST',
        headers: getAuthHeaders(),
        signal,
        openWhenHidden: true,
        onmessage: (event) => {
          if (event.data) {
            onChunk(event.data)
          }
        },
        onerror: (error) => {
          if (signal?.aborted) return
          onError(new Error('Failed to regenerate'))
          throw error
        },
        onopen: async (response) => {
          if (!response.ok) {
            throw new Error(`Regenerate failed (HTTP ${response.status})`)
          }
        },
        onclose: () => onComplete(),
      },
    )
  } catch (error) {
    if (signal?.aborted) {
      onComplete()
      return
    }
    onError(error instanceof Error ? error : new Error('Unknown error'))
  }
}
