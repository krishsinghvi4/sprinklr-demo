import { useState, useRef, useEffect } from 'react'
import { Link } from 'react-router-dom'
import { Send, Bot, User, Loader, Square } from 'lucide-react'
import AssistantMessageContent from './AssistantMessageContent'
import { Message, ChatRequest } from '../types/chat'
import { streamChat, fetchChatHistory } from '../services/chatService'
import { extractWidgetBlock, hasWidgetFence } from '../utils/parseWidgetPayload'
import { expandWidgetInChat, saveTurnToDashboard } from '../services/insightsService'
import type { WidgetSpec } from '../types/widgets'

interface ChatProps {
  userId: string
  conversationId: string
}

export default function Chat({ userId, conversationId }: ChatProps) {
  const [messages, setMessages] = useState<Message[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [lastPrompt, setLastPrompt] = useState<string | null>(null)
  const [isLoadingHistory, setIsLoadingHistory] = useState(true)
  const [saveToast, setSaveToast] = useState<{ message: string; link?: string } | null>(null)
  const [savingMessageId, setSavingMessageId] = useState<string | null>(null)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)
  const abortControllerRef = useRef<AbortController | null>(null)

  useEffect(() => {
    const loadHistory = async () => {
      setIsLoadingHistory(true)
      const history = await fetchChatHistory(conversationId)
      setMessages(history)
      setIsLoadingHistory(false)
      scrollToBottom()
    }
    loadHistory()
  }, [conversationId])

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  useEffect(() => {
    scrollToBottom()
  }, [messages])

  const findUserPromptForMessage = (assistantIndex: number): string => {
    for (let i = assistantIndex - 1; i >= 0; i--) {
      if (messages[i]?.role === 'user') {
        return messages[i].content
      }
    }
    return lastPrompt ?? ''
  }

  const handleSaveToDashboard = async (message: Message, messageIndex: number) => {
    const widgetBlock = extractWidgetBlock(message.content)
    if (!widgetBlock) {
      return
    }
    const prompt = findUserPromptForMessage(messageIndex)
    setSavingMessageId(message.id)
    const result = await saveTurnToDashboard({
      sourceConversationId: conversationId,
      sourceChatMessageId: message.id,
      prompt,
      assistantContent: message.content,
      widgets: widgetBlock.widgets,
    })
    setSavingMessageId(null)
    if (result) {
      setSaveToast({
        message: 'Saved to Insights',
        link: `/insights/${result.dashboardConversationId}`,
      })
      setTimeout(() => setSaveToast(null), 5000)
    } else {
      setError('Failed to save to dashboard')
    }
  }

  const handleExpandWidget = async (widget: WidgetSpec) => {
    return expandWidgetInChat(widget)
  }

  const sendPrompt = async (promptText: string, isRetry = false) => {
    if (!promptText) return

    if (!isRetry) {
      const userMessage: Message = {
        id: Date.now().toString(),
        role: 'user',
        content: promptText,
        timestamp: new Date(),
      }

      setMessages((prev) => [...prev, userMessage])
      setInput('')
      setLastPrompt(promptText)
    }

    setError(null)
    setIsLoading(true)

    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
    }

    const assistantMessage: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '',
      timestamp: new Date(),
    }

    setMessages((prev) => [...prev, assistantMessage])

    const chatRequest: ChatRequest = {
      userId,
      conversationId,
      prompt: promptText,
    }

    abortControllerRef.current?.abort()
    const abortController = new AbortController()
    abortControllerRef.current = abortController

    try {
      await streamChat(
        chatRequest,
        (chunk) => {
          setMessages((prev) => {
            const updated = [...prev]
            const lastMsg = updated[updated.length - 1]
            if (lastMsg && lastMsg.role === 'assistant') {
              lastMsg.content += chunk
            }
            return updated
          })
          scrollToBottom()
        },
        (error) => {
          if (abortController.signal.aborted) return
          console.error('Chat error:', error)
          setError('Failed to get response. Please try again.')
          setIsLoading(false)
        },
        () => {
          if (abortControllerRef.current === abortController) {
            abortControllerRef.current = null
          }
          setIsLoading(false)
        },
        abortController.signal,
      )
    } catch (err) {
      if (abortController.signal.aborted) return
      console.error('Unexpected error:', err)
      setError('An unexpected error occurred')
      setIsLoading(false)
    }
  }

  const handleStopResponse = () => {
    abortControllerRef.current?.abort()
    abortControllerRef.current = null
    setIsLoading(false)
  }

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault()
    await sendPrompt(input.trim())
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      void sendPrompt(input.trim())
    }
  }

  const handleTextAreaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInput(e.target.value)
    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
      inputRef.current.style.height = `${Math.min(inputRef.current.scrollHeight, 150)}px`
    }
  }

  return (
    <div className="flex flex-col h-full">
      {saveToast && (
        <div className="bg-green-50 border-b border-green-200 px-4 py-2 text-sm text-green-800 flex items-center justify-between max-w-4xl mx-auto w-full">
          <span>{saveToast.message}</span>
          {saveToast.link && (
            <Link to={saveToast.link} className="font-medium underline hover:text-green-900">
              View dashboard
            </Link>
          )}
        </div>
      )}

      <div className="flex-1 overflow-y-auto px-4 py-6 space-y-4 max-w-4xl mx-auto w-full">
        {isLoadingHistory ? (
          <div className="h-full flex items-center justify-center">
            <div className="text-center">
              <Loader className="w-8 h-8 mx-auto text-gray-400 mb-4 animate-spin" />
              <p className="text-gray-500 text-lg">Loading conversation...</p>
            </div>
          </div>
        ) : messages.length === 0 && !isLoading ? (
          <div className="h-full flex items-center justify-center">
            <div className="text-center">
              <Bot className="w-16 h-16 mx-auto text-gray-300 mb-4" />
              <p className="text-gray-500 text-lg">
                Start a conversation by typing a message below
              </p>
            </div>
          </div>
        ) : null}

        {messages.map((message, index) => (
          <div
            key={message.id}
            className={`flex ${
              message.role === 'user' ? 'justify-end' : 'justify-start'
            }`}
          >
            <div
              className={`flex gap-3 max-w-2xl ${
                message.role === 'user' ? 'flex-row-reverse' : ''
              }`}
            >
              <div
                className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
                  message.role === 'user'
                    ? 'bg-blue-600'
                    : 'bg-gray-300'
                }`}
              >
                {message.role === 'user' ? (
                  <User className="w-5 h-5 text-white" />
                ) : (
                  <Bot className="w-5 h-5 text-white" />
                )}
              </div>

              <div
                className={`rounded-lg px-4 py-3 ${
                  message.role === 'user'
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-200 text-gray-900'
                }`}
              >
                {message.role === 'assistant' ? (
                  <AssistantMessageContent
                    content={message.content}
                    mode="chat"
                    onSaveToDashboard={
                      hasWidgetFence(message.content)
                        ? () => void handleSaveToDashboard(message, index)
                        : undefined
                    }
                    onExpandWidget={handleExpandWidget}
                    saveDisabled={savingMessageId === message.id}
                  />
                ) : (
                  <p className="whitespace-pre-wrap">{message.content}</p>
                )}
              </div>
            </div>
          </div>
        ))}

        {isLoading &&
          messages[messages.length - 1]?.role === 'assistant' &&
          !messages[messages.length - 1]?.content && (
          <div className="flex justify-start">
            <div className="flex gap-3">
              <div className="flex-shrink-0 w-8 h-8 rounded-full bg-gray-300 flex items-center justify-center">
                <Bot className="w-5 h-5 text-white" />
              </div>
              <div className="flex items-center gap-1 bg-gray-200 rounded-lg px-4 py-3">
                <Loader className="w-4 h-4 text-gray-600 animate-spin" />
                <span className="text-sm text-gray-600">Thinking...</span>
              </div>
            </div>
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-red-700">
            <div className="flex items-center justify-between">
              <span>{error}</span>
              {lastPrompt && (
                <button
                  onClick={() => {
                    setMessages((prev) => prev.slice(0, -1))
                    if (lastPrompt) {
                      void sendPrompt(lastPrompt, true)
                    }
                  }}
                  disabled={isLoading}
                  className="ml-4 px-3 py-1 bg-red-600 text-white rounded hover:bg-red-700 disabled:bg-gray-400 disabled:cursor-not-allowed text-sm font-medium"
                >
                  Retry
                </button>
              )}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="border-t border-gray-200 bg-white px-4 py-4">
        <div className="max-w-4xl mx-auto">
          <form onSubmit={handleSendMessage} className="flex gap-3">
            <textarea
              ref={inputRef}
              value={input}
              onChange={handleTextAreaChange}
              onKeyDown={handleKeyDown}
              placeholder="Type your message... (Shift+Enter for new line)"
              disabled={isLoading}
              rows={1}
              className="flex-1 resize-none rounded-lg border border-gray-300 px-4 py-3 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200 disabled:bg-gray-100 disabled:cursor-not-allowed"
            />
            {isLoading && (
              <button
                type="button"
                onClick={handleStopResponse}
                title="Stop response"
                aria-label="Stop response"
                className="bg-gray-200 text-gray-700 rounded-lg px-3 py-3 hover:bg-gray-300 transition-colors flex items-center justify-center h-fit"
              >
                <Square className="w-4 h-4 fill-current" />
              </button>
            )}
            <button
              type="submit"
              disabled={isLoading || !input.trim()}
              className="bg-blue-600 text-white rounded-lg px-4 py-3 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors flex items-center gap-2 h-fit"
            >
              <Send className="w-5 h-5" />
              <span className="hidden sm:inline">Send</span>
            </button>
          </form>
        </div>
      </div>
    </div>
  )
}
