import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { MessageSquarePlus } from 'lucide-react'
import { useAuth } from '../context/AuthContext'
import { fetchConversations } from '../services/chatService'
import { ConversationSummary } from '../types/chat'

function formatRelativeDate(isoDate: string): string {
  const date = new Date(isoDate)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffDays = Math.floor(diffMs / (1000 * 60 * 60 * 24))

  if (diffDays === 0) {
    return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
  }
  if (diffDays === 1) {
    return 'Yesterday'
  }
  if (diffDays < 7) {
    return date.toLocaleDateString([], { weekday: 'short' })
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' })
}

function createConversationId(): string {
  return 'conv-' + Math.random().toString(36).slice(2, 11)
}

export default function ChatDashboardPage() {
  const navigate = useNavigate()
  const { user, logout } = useAuth()
  const [conversations, setConversations] = useState<ConversationSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const loadConversations = async () => {
      setIsLoading(true)
      const list = await fetchConversations()
      setConversations(list)
      setIsLoading(false)
    }
    loadConversations()
  }, [])

  const handleNewChat = () => {
    navigate(`/chat/${createConversationId()}`)
  }

  const handleOpenChat = (conversationId: string) => {
    navigate(`/chat/${conversationId}`)
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-3xl mx-auto px-4 py-4 flex justify-between items-center">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Sprinklr Chat</h1>
            {user?.email && (
              <p className="text-sm text-gray-500 mt-1">{user.email}</p>
            )}
          </div>
          <div className="flex items-center gap-3">
            <Link
              to="/profile"
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              Profile
            </Link>
            <button
              onClick={handleNewChat}
              className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium"
            >
              <MessageSquarePlus className="w-4 h-4" />
              New Chat
            </button>
            <button
              onClick={logout}
              className="px-3 py-2 text-sm text-gray-600 hover:text-gray-900"
            >
              Log out
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-3xl w-full mx-auto px-4 py-6">
        {isLoading ? (
          <p className="text-gray-500 text-sm">Loading conversations...</p>
        ) : conversations.length === 0 ? (
          <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-8 text-center">
            <p className="text-gray-600 mb-4">No conversations yet.</p>
            <button
              onClick={handleNewChat}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium"
            >
              Start your first chat
            </button>
          </div>
        ) : (
          <ul className="space-y-2">
            {conversations.map((conversation) => (
              <li key={conversation.id}>
                <button
                  onClick={() => handleOpenChat(conversation.id)}
                  className="w-full text-left bg-white rounded-lg border border-gray-200 shadow-sm px-4 py-3 hover:border-blue-300 hover:shadow transition"
                >
                  <div className="flex justify-between items-start gap-4">
                    <p className="text-gray-900 text-sm font-medium line-clamp-2 flex-1">
                      {conversation.preview}
                    </p>
                    <span className="text-xs text-gray-400 whitespace-nowrap shrink-0">
                      {formatRelativeDate(conversation.updatedAt)}
                    </span>
                  </div>
                </button>
              </li>
            ))}
          </ul>
        )}
      </main>
    </div>
  )
}
