import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronDown, ChevronUp, Sparkles } from 'lucide-react'
import MixedMarkdownContent from '../MixedMarkdownContent'
import WidgetRenderer from './WidgetRenderer'
import { parseWidgetPayload } from '../../utils/parseWidgetPayload'
import type { WidgetSpec } from '../../types/widgets'

export interface WidgetBlockProps {
  widgetJson: string
  widgets?: WidgetSpec[]
  mode?: 'chat' | 'dashboard'
  extendedInsights?: Record<string, string>
  onSaveToDashboard?: () => void
  onDeleteTurn?: () => void
  onExpandWidget?: (widget: WidgetSpec) => Promise<string | null>
  saveDisabled?: boolean
  isAlreadySaved?: boolean
  dashboardLink?: string
}

export default function WidgetBlock({
  widgetJson,
  widgets: widgetsProp,
  mode = 'chat',
  extendedInsights = {},
  onSaveToDashboard,
  onDeleteTurn,
  onExpandWidget,
  saveDisabled = false,
  isAlreadySaved = false,
  dashboardLink,
}: WidgetBlockProps) {
  const block = widgetsProp
    ? { version: 1 as const, widgets: widgetsProp }
    : parseWidgetPayload(widgetJson)

  if (!block) {
    return (
      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
        <p className="text-sm text-amber-800 font-medium">Could not render analytics widgets</p>
        <pre className="mt-2 text-xs overflow-x-auto bg-white p-2 rounded border max-h-40">
          {widgetJson}
        </pre>
      </div>
    )
  }

  return (
    <div className="space-y-3">
      <div className={mode === 'chat' ? 'grid grid-cols-1 gap-4' : 'grid grid-cols-1 md:grid-cols-2 gap-4'}>
        {block.widgets.map((widget) => (
          <WidgetCard
            key={widget.id}
            widget={widget}
            extendedInsight={extendedInsights[widget.id]}
            onExpand={onExpandWidget}
          />
        ))}
      </div>
      <div className="flex gap-2 pt-1">
        {mode === 'chat' && isAlreadySaved && dashboardLink && (
          <Link
            to={dashboardLink}
            className="text-xs px-3 py-1.5 bg-green-50 text-green-700 border border-green-200 rounded-md hover:bg-green-100"
          >
            Saved to Insights
          </Link>
        )}
        {mode === 'chat' && onSaveToDashboard && !isAlreadySaved && (
          <button
            type="button"
            onClick={onSaveToDashboard}
            disabled={saveDisabled}
            className="text-xs px-3 py-1.5 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            Save to Dashboard
          </button>
        )}
        {mode === 'dashboard' && onDeleteTurn && (
          <button
            type="button"
            onClick={onDeleteTurn}
            className="text-xs px-3 py-1.5 bg-red-50 text-red-700 border border-red-200 rounded-md hover:bg-red-100"
          >
            Delete turn
          </button>
        )}
      </div>
    </div>
  )
}

interface WidgetCardProps {
  widget: WidgetSpec
  extendedInsight?: string
  onExpand?: (widget: WidgetSpec) => Promise<string | null>
}

function WidgetCard({ widget, extendedInsight, onExpand }: WidgetCardProps) {
  const [expanded, setExpanded] = useState(Boolean(extendedInsight))
  const [insight, setInsight] = useState(extendedInsight ?? '')
  const [loading, setLoading] = useState(false)

  const handleExpand = async () => {
    if (insight && expanded) {
      setExpanded(false)
      return
    }
    if (insight) {
      setExpanded(true)
      return
    }
    if (!onExpand) {
      return
    }
    setLoading(true)
    try {
      const result = await onExpand(widget)
      if (result) {
        setInsight(result)
        setExpanded(true)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-white rounded-lg border border-gray-200 p-4 shadow-sm min-w-0 overflow-hidden">
      <h4 className="font-semibold text-gray-900 text-sm">{widget.title}</h4>
      {widget.description && (
        <p className="text-xs text-gray-500 mt-0.5">{widget.description}</p>
      )}
      <div className="mt-3">
        <WidgetRenderer widget={widget} />
      </div>
      {onExpand && (
        <div className="mt-3 border-t border-gray-100 pt-2">
          <button
            type="button"
            onClick={() => void handleExpand()}
            disabled={loading}
            className="flex items-center gap-1 text-xs text-blue-600 hover:text-blue-800"
          >
            <Sparkles className="w-3 h-3" />
            {loading ? 'Generating insights…' : expanded ? 'Hide extended insights' : 'Extended insights'}
            {expanded ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
          </button>
          {expanded && insight && (
            <MixedMarkdownContent
              content={insight}
              className="mt-2 text-gray-700"
            />
          )}
        </div>
      )}
    </div>
  )
}
