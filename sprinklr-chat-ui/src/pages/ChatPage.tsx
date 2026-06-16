import { Link, useParams } from 'react-router-dom'
import Chat from '../components/Chat'
import { useAuth } from '../context/AuthContext'

export default function ChatPage() {
  const { conversationId } = useParams<{ conversationId: string }>()
  const { user } = useAuth()

  if (!conversationId || user === null) {
    return <div>Loading...</div>
  }

  const userId = user.userId

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      <header className="bg-white shadow-sm border-b border-gray-200">
        <div className="max-w-4xl mx-auto px-4 py-4 flex justify-between items-center">
          <div className="flex items-center gap-4">
            <Link
              to="/"
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              ← Back to chats
            </Link>
            <h1 className="text-xl font-bold text-gray-900">Sprinklr Chat</h1>
          </div>
          <Link
            to="/profile"
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            Profile
          </Link>
        </div>
      </header>
      <main className="flex-1 overflow-hidden">
        <Chat key={`${userId}-${conversationId}`} userId={userId} conversationId={conversationId} />
      </main>
    </div>
  )
}
