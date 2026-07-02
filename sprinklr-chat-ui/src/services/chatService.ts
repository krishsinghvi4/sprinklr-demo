import { fetchEventSource } from '@microsoft/fetch-event-source'
import { ChatRequest, ConversationSummary, Message } from '../types/chat'

const CHAT_ENDPOINT = '/api/v1/chat/stream'
const HISTORY_ENDPOINT = '/api/v1/chat/history'
const CONVERSATIONS_ENDPOINT = '/api/v1/chat/conversations'

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

export async function streamChat(
  request: ChatRequest,
  onChunk: (chunk: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void,
  signal?: AbortSignal,
): Promise<void> {
  try {
    await fetchEventSource(CHAT_ENDPOINT, {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify(request),
      signal,
      openWhenHidden: true,
      onmessage: (event) => {
        if (event.data) {
          onChunk(event.data)
        }
      },
      onerror: (error) => {
        if (signal?.aborted) {
          return
        }
        console.error('SSE Error:', error)
        onError(new Error('Failed to stream chat response'))
        throw error
      },
      onopen: async (response) => {
        if (!response.ok) {
          const message = `Chat request failed (HTTP ${response.status})`
          onError(new Error(message))
          throw new Error(message)
        }
      },
      onclose: () => {
        onComplete()
      },
    })
  } catch (error) {
    if (signal?.aborted || (error instanceof DOMException && error.name === 'AbortError')) {
      onComplete()
      return
    }
    const err = error instanceof Error ? error : new Error('Unknown error')
    onError(err)
  }
}

export async function fetchConversations(): Promise<ConversationSummary[]> {
  try {
    const response = await fetch(CONVERSATIONS_ENDPOINT, {
      method: 'GET',
      headers: getAuthHeaders(),
    })

    if (!response.ok) {
      console.warn(`Failed to fetch conversations: HTTP ${response.status}`)
      return []
    }

    const data = await response.json()
    if (!data.conversations || !Array.isArray(data.conversations)) {
      return []
    }

    return data.conversations as ConversationSummary[]
  } catch (error) {
    console.warn('Error fetching conversations:', error)
    return []
  }
}

export async function deleteConversation(conversationId: string): Promise<boolean> {
  try {
    const response = await fetch(
      `${CONVERSATIONS_ENDPOINT}/${encodeURIComponent(conversationId)}`,
      {
        method: 'DELETE',
        headers: getAuthHeaders(),
      }
    )
    return response.ok
  } catch (error) {
    console.warn('Error deleting conversation:', error)
    return false
  }
}

export async function fetchChatHistory(conversationId: string, limit: number = 50): Promise<Message[]> {
  try {
    console.log(`Fetching chat history for conversation: ${conversationId}`)
    const response = await fetch(
      `${HISTORY_ENDPOINT}?conversationId=${encodeURIComponent(conversationId)}&limit=${limit}`,
      {
        method: 'GET',
        headers: getAuthHeaders(),
      }
    )

    if (!response.ok) {
      console.warn(`Failed to fetch chat history: HTTP ${response.status}, treating as new conversation`)
      return []
    }

    const data = await response.json()
    console.log(`Received ${data.messages?.length || 0} messages from history`)
    
    if (!data.messages || !Array.isArray(data.messages)) {
      console.warn('No messages in response, starting fresh conversation')
      return []
    }
    
    // Map the backend message format to frontend Message format
    const messages = data.messages
      .filter((msg: { role?: string; content?: string }) =>
        (msg.role === 'user' || msg.role === 'assistant') && Boolean(msg.content?.trim())
      )
      .map((msg: { id: string; role: 'user' | 'assistant'; content: string; createdAt: string }) => ({
        id: msg.id,
        role: msg.role,
        content: msg.content || '',
        timestamp: new Date(msg.createdAt),
      }))
    
    console.log(`Successfully loaded ${messages.length} messages`)
    return messages
  } catch (error) {
    console.warn('Error fetching chat history (new conversation?):', error)
    return []
  }
}

