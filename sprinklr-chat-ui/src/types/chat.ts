export interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: Date
}

export interface ChatRequest {
  userId: string
  conversationId: string
  prompt: string
}

export interface ApiResponse {
  event: string
  data: string
}
