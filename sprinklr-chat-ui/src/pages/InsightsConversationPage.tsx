import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
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
  const [conversation, setConversation] = useState<DashboardConversationDetail | null>(null)
  const [turns, setTurns] = useState<DashboardTurnDetail[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [regeneratingTurnId, setRegeneratingTurnId] = useState<string | null>(null)
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

  const galleryItems = useMemo<GalleryWidget[]>(() => {
    return turns.flatMap((turn) =>
      turn.widgets.map((widget) => ({
        widget,
        turnId: turn.id,
        turnPrompt: turn.prompt,
      })),
    )
  }, [turns])

  const mergedExtendedInsights = useMemo(() => {
    const merged: Record<string, string> = {}
    for (const turn of turns) {
      for (const [widgetId, markdown] of Object.entries(turn.extendedInsights)) {
        merged[`${turn.id}:${widgetId}`] = markdown
      }
    }
    return merged
  }, [turns])

  const handlePromptBlur = async (turnId: string, prompt: string) => {
    if (!dashboardConversationId) return
    const updated = await updateTurnPrompt(dashboardConversationId, turnId, prompt)
    if (updated) {
      setTurns((prev) => prev.map((t) => (t.id === turnId ? updated : t)))
    }
  }

  const handleRegenerate = async (turn: DashboardTurnDetail) => {
    if (!dashboardConversationId || regeneratingTurnId) return

    abortRef.current?.abort()
    const abortController = new AbortController()
    abortRef.current = abortController
    setRegeneratingTurnId(turn.id)

    await streamRegenerateTurn(
      dashboardConversationId,
      turn.id,
      () => {},
      async () => {
        setRegeneratingTurnId(null)
        abortRef.current = null
        const refreshed = await fetchDashboardTurn(dashboardConversationId, turn.id)
        if (refreshed) {
          setTurns((prev) => prev.map((t) => (t.id === turn.id ? refreshed : t)))
        }
      },
      () => {
        setRegeneratingTurnId(null)
        abortRef.current = null
      },
      abortController.signal,
    )
  }

  const handleDeleteTurn = async (turnId: string) => {
    if (!dashboardConversationId) return
    if (!confirm('Delete this saved turn?')) return
    const ok = await deleteDashboardTurn(dashboardConversationId, turnId)
    if (ok) {
      setTurns((prev) => prev.filter((t) => t.id !== turnId))
      void load()
    }
  }

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
        ) : (
          <>
            <header className="bg-white rounded-xl border border-gray-200 p-5 shadow-sm">
              <h2 className="text-xl font-semibold text-gray-900">{conversation.title}</h2>
              {conversation.preview && (
                <p className="text-sm text-gray-600 mt-2">{conversation.preview}</p>
              )}
              <p className="text-xs text-gray-400 mt-3">
                From chat{' '}
                <Link to={`/chat/${conversation.sourceConversationId}`} className="text-blue-600 hover:underline">
                  {conversation.sourceConversationId}
                </Link>
                {' · '}
                {conversation.turnCount} saved turn{conversation.turnCount !== 1 ? 's' : ''}
              </p>
            </header>

            <section>
              <InsightsWidgetGallery
                items={galleryItems}
                extendedInsights={mergedExtendedInsights}
                onExpandWidget={handleExpandWidget}
              />
            </section>

            {turns.length > 0 && (
              <details className="bg-white rounded-xl border border-gray-200 shadow-sm">
                <summary className="px-5 py-4 cursor-pointer text-sm font-medium text-gray-700 hover:bg-gray-50 rounded-xl">
                  Manage saved analytics ({turns.length})
                </summary>
                <div className="border-t border-gray-100 divide-y divide-gray-100">
                  {turns.map((turn) => (
                    <TurnManageRow
                      key={turn.id}
                      turn={turn}
                      isRegenerating={regeneratingTurnId === turn.id}
                      onPromptBlur={(prompt) => void handlePromptBlur(turn.id, prompt)}
                      onRegenerate={() => void handleRegenerate(turn)}
                      onDelete={() => void handleDeleteTurn(turn.id)}
                    />
                  ))}
                </div>
              </details>
            )}
          </>
        )}
      </main>
    </div>
  )
}

interface TurnManageRowProps {
  turn: DashboardTurnDetail
  isRegenerating: boolean
  onPromptBlur: (prompt: string) => void
  onRegenerate: () => void
  onDelete: () => void
}

function TurnManageRow({
  turn,
  isRegenerating,
  onPromptBlur,
  onRegenerate,
  onDelete,
}: TurnManageRowProps) {
  const [prompt, setPrompt] = useState(turn.prompt)

  useEffect(() => {
    setPrompt(turn.prompt)
  }, [turn.prompt])

  return (
    <div className="px-5 py-4">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <label className="text-xs font-medium text-gray-500 uppercase tracking-wide">Prompt</label>
          <textarea
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            onBlur={() => {
              if (prompt.trim() !== turn.prompt) {
                onPromptBlur(prompt.trim())
              }
            }}
            rows={2}
            className="mt-1 w-full text-sm border border-gray-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-blue-200 focus:border-blue-500"
          />
          {isRegenerating && (
            <div className="flex items-center gap-2 text-xs text-gray-500 mt-2">
              <Loader className="w-3 h-3 animate-spin" />
              Regenerating analytics…
            </div>
          )}
        </div>
        <div className="flex items-center gap-2 pt-5 shrink-0">
          {turn.version > 1 && (
            <span className="text-xs bg-blue-100 text-blue-700 px-2 py-0.5 rounded-full">
              v{turn.version}
            </span>
          )}
          <button
            type="button"
            onClick={onRegenerate}
            disabled={isRegenerating}
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
            className="p-1.5 text-gray-400 hover:text-red-600 rounded"
            title="Delete turn"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  )
}
