import { fetchEventSource } from '@microsoft/fetch-event-source'
import { ChatRequest, Message } from '../types/chat'

const API_BASE_URL = 'http://localhost:8080'
const CHAT_ENDPOINT = `${API_BASE_URL}/api/v1/chat/stream`
const HISTORY_ENDPOINT = `${API_BASE_URL}/api/v1/chat/history`

export async function streamChat(
  request: ChatRequest,
  onChunk: (chunk: string) => void,
  onError: (error: Error) => void,
  onComplete: () => void,
): Promise<void> {
  try {
    let messageCount = 0
    let totalChars = 0
    
    await fetchEventSource(CHAT_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(request),
      onmessage: (event) => {
        if (event.data) {
          messageCount++
          totalChars += event.data.length
          console.log(`[SSE Event #${messageCount}] Data: "${event.data.substring(0, 50)}..." (length: ${event.data.length})`)
          onChunk(event.data)
        }
      },
      onerror: (error) => {
        console.error('SSE Error:', error)
        onError(new Error('Failed to stream chat response'))
      },
      onopen: async (response) => {
        console.log('[SSE] Connection opened, status:', response.status)
        if (!response.ok) {
          throw new Error(`HTTP ${response.status}`)
        }
      },
      onclose: () => {
        console.log(`[SSE] Stream closed. Total events: ${messageCount}, total chars: ${totalChars}`)
        onComplete()
      },
    })
  } catch (error) {
    const err = error instanceof Error ? error : new Error('Unknown error')
    onError(err)
  }
}

export async function fetchChatHistory(conversationId: string, limit: number = 50): Promise<Message[]> {
  try {
    console.log(`Fetching chat history for conversation: ${conversationId}`)
    const response = await fetch(
      `${HISTORY_ENDPOINT}?conversationId=${encodeURIComponent(conversationId)}&limit=${limit}`,
      {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
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
    const messages = data.messages.map((msg: any) => ({
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

