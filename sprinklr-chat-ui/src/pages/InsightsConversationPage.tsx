import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import Markdown from 'react-markdown'
import { Loader, RefreshCw, Trash2 } from 'lucide-react'
import AppHeader from '../components/AppHeader'
import WidgetBlock from '../components/widgets/WidgetBlock'
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
import type { WidgetSpec } from '../types/widgets'

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

    let content = ''
    await streamRegenerateTurn(
      dashboardConversationId,
      turn.id,
      (chunk) => {
        content += chunk
      },
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

  const handleExpandWidget = (turn: DashboardTurnDetail) => async (widget: WidgetSpec) => {
    if (!dashboardConversationId) return null
    return expandDashboardWidget(
      dashboardConversationId,
      turn.id,
      widget.id,
      turn.toolResultSnapshot ?? undefined,
    )
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

      <main className="flex-1 max-w-4xl mx-auto w-full px-4 py-6 space-y-6">
        {isLoading ? (
          <p className="text-gray-500 text-center py-12">Loading…</p>
        ) : !conversation ? (
          <p className="text-gray-500 text-center py-12">Conversation not found</p>
        ) : (
          <>
            <div className="flex items-center justify-between">
              <div>
                <h2 className="text-lg font-semibold text-gray-900">{conversation.title}</h2>
                <p className="text-sm text-gray-500">
                  From chat{' '}
                  <Link to={`/chat/${conversation.sourceConversationId}`} className="text-blue-600 hover:underline">
                    {conversation.sourceConversationId}
                  </Link>
                </p>
              </div>
            </div>

            {turns.length === 0 ? (
              <p className="text-gray-500 text-center py-8">No saved turns</p>
            ) : (
              turns.map((turn) => (
                <TurnCard
                  key={turn.id}
                  turn={turn}
                  isRegenerating={regeneratingTurnId === turn.id}
                  onPromptBlur={(prompt) => void handlePromptBlur(turn.id, prompt)}
                  onRegenerate={() => void handleRegenerate(turn)}
                  onDelete={() => void handleDeleteTurn(turn.id)}
                  onExpandWidget={handleExpandWidget(turn)}
                />
              ))
            )}
          </>
        )}
      </main>
    </div>
  )
}

interface TurnCardProps {
  turn: DashboardTurnDetail
  isRegenerating: boolean
  onPromptBlur: (prompt: string) => void
  onRegenerate: () => void
  onDelete: () => void
  onExpandWidget: (widget: WidgetSpec) => Promise<string | null>
}

function TurnCard({
  turn,
  isRegenerating,
  onPromptBlur,
  onRegenerate,
  onDelete,
  onExpandWidget,
}: TurnCardProps) {
  const [prompt, setPrompt] = useState(turn.prompt)

  useEffect(() => {
    setPrompt(turn.prompt)
  }, [turn.prompt])

  return (
    <article className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
      <div className="p-4 border-b border-gray-100 bg-gray-50">
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1">
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
          </div>
          <div className="flex items-center gap-2 pt-5">
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

      <div className="p-4 space-y-4">
        {isRegenerating && (
          <div className="flex items-center gap-2 text-sm text-gray-500">
            <Loader className="w-4 h-4 animate-spin" />
            Regenerating analytics…
          </div>
        )}

        {turn.narrative && (
          <div className="prose prose-sm max-w-none">
            <Markdown>{turn.narrative}</Markdown>
          </div>
        )}

        {turn.widgets.length > 0 && (
          <WidgetBlock
            widgetJson=""
            widgets={turn.widgets}
            mode="dashboard"
            extendedInsights={turn.extendedInsights}
            onDeleteTurn={onDelete}
            onExpandWidget={onExpandWidget}
          />
        )}
      </div>
    </article>
  )
}
