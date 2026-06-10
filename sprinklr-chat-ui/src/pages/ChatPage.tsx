import { useState, useEffect } from 'react'
import Chat from '../components/Chat'
import { useAuth } from '../context/AuthContext'

function conversationStorageKey(userId: string) {
  return `conversationId:${userId}`
}

export default function ChatPage() {
  const { user } = useAuth()
  const [conversationId, setConversationId] = useState<string>('')

  useEffect(() => {
    if (!user?.userId) {
      setConversationId('')
      return
    }

    const storageKey = conversationStorageKey(user.userId)
    const storedConversationId = localStorage.getItem(storageKey)
    if (storedConversationId) {
      setConversationId(storedConversationId)
      return
    }

    const newConversationId = 'conv-' + Math.random().toString(36).slice(2, 11)
    localStorage.setItem(storageKey, newConversationId)
    setConversationId(newConversationId)
  }, [user?.userId])

  const handleNewConversation = () => {
    if (!user?.userId) return

    const newConversationId = 'conv-' + Math.random().toString(36).slice(2, 11)
    localStorage.setItem(conversationStorageKey(user.userId), newConversationId)
    setConversationId(newConversationId)
  }

  if (!conversationId || user === null) {
    return <div>Loading...</div>
  }

  const userId = user.userId

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-4xl mx-auto px-4 py-4 flex justify-between items-start">
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Sprinklr Chat</h1>
            <p className="text-xs text-gray-500 mt-1">
              Conversation ID: {conversationId}
            </p>
          </div>
          <button
            onClick={handleNewConversation}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium"
          >
            New Chat
          </button>
        </div>
      </header>
      <main className="flex-1 overflow-hidden">
        <Chat key={`${userId}-${conversationId}`} userId={userId} conversationId={conversationId} />
      </main>
    </div>
  )
}
