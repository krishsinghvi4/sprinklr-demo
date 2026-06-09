import { useState, useEffect } from 'react'
import Chat from '../components/Chat'
import { useAuth } from '../context/AuthContext'

export default function ChatPage() {
  const { user } = useAuth()
  const [conversationId, setConversationId] = useState<string>('')

  useEffect(() => {
    const storedConversationId = localStorage.getItem('conversationId')
    if (storedConversationId) {
      setConversationId(storedConversationId)
    } else {
      const newConversationId = 'conv-' + Math.random().toString(36).substr(2, 9)
      localStorage.setItem('conversationId', newConversationId)
      setConversationId(newConversationId)
    }
  }, [])

  const handleNewConversation = () => {
    const newConversationId = 'conv-' + Math.random().toString(36).substr(2, 9)
    localStorage.setItem('conversationId', newConversationId)
    setConversationId(newConversationId)
  }

  if (!conversationId) {
    return <div>Loading...</div>
  }

  if (user === null) {
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
        <Chat userId={userId} conversationId={conversationId} />
      </main>
    </div>
  )
}
