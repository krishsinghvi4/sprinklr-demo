import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Loader, RefreshCw, Trash2 } from 'lucide-react'
import AppHeader from '../components/AppHeader'
import InsightsWidgetGallery, { type GalleryWidget } from '../components/widgets/InsightsWidgetGallery'
import {
  deleteDashboardTurn,
  expandDashboardWidget,
  fetchDashboardConversation,
  fetchDashboardTurn,
  streamRegenerateTurn,
  updateTurnPrompt,
  type DashboardConversationDetail,
  type DashboardTurnDetail,
} from '../services/insightsService'

export default function InsightsConversationPage() {
  const { dashboardConversationId } = useParams<{ dashboardConversationId: string }>()
  const navigate = useNavigate()
  const [conversation, setConversation] = useState<DashboardConversationDetail | null>(null)
  const [turns, setTurns] = useState<DashboardTurnDetail[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [regeneratingTurnId, setRegeneratingTurnId] = useState<string | null>(null)
  const [regenStatus, setRegenStatus] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)

  const load = useCallback(async () => {
    if (!dashboardConversationId) return
    setIsLoading(true)
    const conv = await fetchDashboardConversation(dashboardConversationId)
    setConversation(conv)
    if (conv) {
      const turnDetails = await Promise.all(
        conv.turns.map((t) => fetchDashboardTurn(dashboardConversationId, t.id)),
      )
      setTurns(turnDetails.filter((t): t is DashboardTurnDetail => t !== null))
    }
    setIsLoading(false)
  }, [dashboardConversationId])

  useEffect(() => {
    void load()
  }, [load])

  const handleExpandWidget = useCallback(
    (item: GalleryWidget) => {
      if (!dashboardConversationId) return Promise.resolve(null)
      const turn = turns.find((t) => t.id === item.turnId)
      return expandDashboardWidget(
        dashboardConversationId,
        item.turnId,
        item.widget.id,
        turn?.toolResultSnapshot ?? undefined,
      )
    },
    [dashboardConversationId, turns],
  )

  const handleRegenerate = async (turn: DashboardTurnDetail, prompt: string) => {
    if (!dashboardConversationId || regeneratingTurnId) return

    setError(null)
    setRegenStatus(null)

    const trimmedPrompt = prompt.trim()
    if (trimmedPrompt && trimmedPrompt !== turn.prompt) {
      await updateTurnPrompt(dashboardConversationId, turn.id, trimmedPrompt)
    }

    abortRef.current?.abort()
    const abortController = new AbortController()
    abortRef.current = abortController
    setRegeneratingTurnId(turn.id)

    await streamRegenerateTurn(
      dashboardConversationId,
      turn.id,
      (chunk) => {
        setRegenStatus((prev) => (prev ?? '') + chunk)
      },
      async () => {
        setRegeneratingTurnId(null)
        setRegenStatus(null)
        abortRef.current = null
        await load()
      },
      (err) => {
        setRegeneratingTurnId(null)
        setRegenStatus(null)
        abortRef.current = null
        setError(err.message)
      },
      abortController.signal,
    )
  }

  const handleDeleteTurn = async (turnId: string) => {
    if (!dashboardConversationId) return
    if (!confirm('Delete this saved insight?')) return
    const ok = await deleteDashboardTurn(dashboardConversationId, turnId)
    if (ok) {
      const remaining = turns.filter((t) => t.id !== turnId)
      if (remaining.length === 0) {
        navigate('/insights')
        return
      }
      void load()
    }
  }

  if (!dashboardConversationId) {
    return null
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <AppHeader
        title="Insights"
        backLink={{ to: '/insights', label: 'All insights' }}
      />

      <main className="flex-1 max-w-6xl mx-auto w-full px-4 py-6 space-y-6">
        {isLoading ? (
          <p className="text-gray-500 text-center py-12">Loading…</p>
        ) : !conversation ? (
          <p className="text-gray-500 text-center py-12">Conversation not found</p>
        ) : turns.length === 0 ? (
          <p className="text-gray-500 text-center py-12">No saved insights</p>
        ) : (
          <>
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg px-4 py-3 text-sm text-red-700">
                {error}
              </div>
            )}

            {turns.map((turn) => (
              <TurnInsightBlock
                key={turn.id}
                turn={turn}
                sourceConversationId={conversation.sourceConversationId}
                isRegenerating={regeneratingTurnId === turn.id}
                regenStatus={regeneratingTurnId === turn.id ? regenStatus : null}
                onRegenerate={(prompt) => void handleRegenerate(turn, prompt)}
                onDelete={() => void handleDeleteTurn(turn.id)}
                onExpandWidget={handleExpandWidget}
              />
            ))}
          </>
        )}
      </main>
    </div>
  )
}

interface TurnInsightBlockProps {
  turn: DashboardTurnDetail
  sourceConversationId: string
  isRegenerating: boolean
  regenStatus: string | null
  onRegenerate: (prompt: string) => void
  onDelete: () => void
  onExpandWidget: (item: GalleryWidget) => Promise<string | null>
}

function TurnInsightBlock({
  turn,
  sourceConversationId,
  isRegenerating,
  regenStatus,
  onRegenerate,
  onDelete,
  onExpandWidget,
}: TurnInsightBlockProps) {
  const [prompt, setPrompt] = useState(turn.prompt)

  useEffect(() => {
    setPrompt(turn.prompt)
  }, [turn.prompt])

  const galleryItems: GalleryWidget[] = turn.widgets.map((widget) => ({
    widget,
    turnId: turn.id,
    turnPrompt: turn.prompt,
  }))

  const extendedInsights: Record<string, string> = {}
  for (const [widgetId, markdown] of Object.entries(turn.extendedInsights)) {
    extendedInsights[`${turn.id}:${widgetId}`] = markdown
  }

  return (
    <section className="space-y-4">
      <header className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
        <div className="flex items-start gap-3">
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            rows={2}
            disabled={isRegenerating}
            className="flex-1 text-sm border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-200 focus:border-blue-500 disabled:bg-gray-50"
            placeholder="Analytics prompt…"
          />
          <div className="flex items-center gap-2 shrink-0 pt-0.5">
            {turn.version > 1 && (
              <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">
                v{turn.version}
              </span>
            )}
            <button
              type="button"
              onClick={() => onRegenerate(prompt)}
              disabled={isRegenerating || !prompt.trim()}
              className="inline-flex items-center gap-1 text-xs px-3 py-1.5 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
            >
              {isRegenerating ? (
                <Loader className="w-3 h-3 animate-spin" />
              ) : (
                <RefreshCw className="w-3 h-3" />
              )}
              Regenerate
            </button>
            <button
              type="button"
              onClick={onDelete}
              disabled={isRegenerating}
              className="p-1.5 text-gray-400 hover:text-red-600 rounded disabled:opacity-50"
              title="Delete"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </div>

        {turn.narrative && (
          <p className="text-sm text-gray-600 mt-3 line-clamp-3">{turn.narrative}</p>
        )}

        <p className="text-xs text-gray-400 mt-3">
          From chat{' '}
          <Link to={`/chat/${sourceConversationId}`} className="text-blue-600 hover:underline">
            {sourceConversationId}
          </Link>
        </p>

        {isRegenerating && (
          <div className="flex items-start gap-2 text-xs text-gray-500 mt-3">
            <Loader className="w-3 h-3 animate-spin mt-0.5 shrink-0" />
            <span className="whitespace-pre-wrap">{regenStatus || 'Regenerating analytics…'}</span>
          </div>
        )}
      </header>

      <InsightsWidgetGallery
        items={galleryItems}
        extendedInsights={extendedInsights}
        onExpandWidget={onExpandWidget}
      />
    </section>
  )
}
