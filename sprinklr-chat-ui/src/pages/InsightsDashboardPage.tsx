import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { BarChart3, MessageSquare, Trash2 } from 'lucide-react'
import AppHeader from '../components/AppHeader'
import {
  deleteDashboardConversation,
  fetchDashboardConversations,
  type DashboardConversationSummary,
} from '../services/insightsService'

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

export default function InsightsDashboardPage() {
  const navigate = useNavigate()
  const [conversations, setConversations] = useState<DashboardConversationSummary[]>([])
  const [isLoading, setIsLoading] = useState(true)

  const load = async () => {
    setIsLoading(true)
    const list = await fetchDashboardConversations()
    setConversations(list)
    setIsLoading(false)
  }

  useEffect(() => {
    void load()
  }, [])

  const handleDelete = async (e: React.MouseEvent, id: string) => {
    e.preventDefault()
    e.stopPropagation()
    if (!confirm('Delete this insights conversation and all saved turns?')) {
      return
    }
    const ok = await deleteDashboardConversation(id)
    if (ok) {
      setConversations((prev) => prev.filter((c) => c.id !== id))
    }
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <AppHeader title="Insights Dashboard" />

      <main className="flex-1 max-w-3xl mx-auto w-full px-4 py-6">
        {isLoading ? (
          <p className="text-gray-500 text-center py-12">Loading insights…</p>
        ) : conversations.length === 0 ? (
          <div className="text-center py-16">
            <BarChart3 className="w-16 h-16 mx-auto text-gray-300 mb-4" />
            <p className="text-gray-600 text-lg">No saved analytics yet</p>
            <p className="text-gray-500 text-sm mt-2">
              Ask for ticket analytics in chat, then click &quot;Save to Dashboard&quot;
            </p>
            <Link to="/" className="inline-block mt-4 text-blue-600 hover:text-blue-800 text-sm">
              Go to Chats
            </Link>
          </div>
        ) : (
          <ul className="space-y-3">
            {conversations.map((conv) => (
              <li key={conv.id}>
                <button
                  type="button"
                  onClick={() => navigate(`/insights/${conv.id}`)}
                  className="w-full text-left bg-white rounded-lg border border-gray-200 p-4 hover:border-blue-300 hover:shadow-sm transition-all group"
                >
                  <div className="flex justify-between items-start gap-3">
                    <div className="min-w-0 flex-1">
                      <p className="font-medium text-gray-900 truncate">{conv.title}</p>
                      <p className="text-sm text-gray-500 truncate mt-0.5">{conv.preview}</p>
                      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
                        <span>{conv.turnCount} saved turn{conv.turnCount !== 1 ? 's' : ''}</span>
                        <span className="flex items-center gap-1">
                          <MessageSquare className="w-3 h-3" />
                          {conv.sourceConversationId}
                        </span>
                        <span>{formatRelativeDate(conv.updatedAt)}</span>
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={(e) => void handleDelete(e, conv.id)}
                      className="opacity-0 group-hover:opacity-100 p-1.5 text-gray-400 hover:text-red-600 rounded"
                      title="Delete"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
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
