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

export interface ConversationSummary {
  id: string
  preview: string
  createdAt: string
  updatedAt: string
}

export interface ApiResponse {
  event: string
  data: string
}
