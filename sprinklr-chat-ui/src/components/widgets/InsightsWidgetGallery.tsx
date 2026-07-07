import { Link } from 'react-router-dom'
import { useState } from 'react'
import { ChevronDown, ChevronUp, Sparkles } from 'lucide-react'
import MixedMarkdownContent from '../MixedMarkdownContent'
import WidgetRenderer from './WidgetRenderer'
import type { WidgetSpec } from '../../types/widgets'

export interface GalleryWidget {
  widget: WidgetSpec
  turnId: string
  turnPrompt: string
}

export interface InsightsWidgetGalleryProps {
  items: GalleryWidget[]
  extendedInsights?: Record<string, string>
  onExpandWidget?: (item: GalleryWidget) => Promise<string | null>
  sourceConversationId?: string
  emptyMessage?: string
}

function gridSpanClass(type: WidgetSpec['type']): string {
  switch (type) {
    case 'kpi':
    case 'timeline':
      return 'md:col-span-2'
    default:
      return 'md:col-span-1'
  }
}

export default function InsightsWidgetGallery({
  items,
  extendedInsights = {},
  onExpandWidget,
  sourceConversationId,
  emptyMessage,
}: InsightsWidgetGalleryProps) {
  const kpiItems = items.filter((item) => item.widget.type === 'kpi')
  const chartItems = items.filter((item) => item.widget.type !== 'kpi')

  if (items.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-200 p-8 text-center shadow-sm">
        <p className="text-sm font-medium text-gray-700">No analytics for this prompt</p>
        <p className="text-sm text-gray-500 mt-2 max-w-md mx-auto">
          {emptyMessage ??
            'Try asking a question in chat that benefits from trends, distributions, or comparisons — then save the result here.'}
        </p>
        {sourceConversationId && (
          <Link
            to={`/chat/${sourceConversationId}`}
            className="inline-block mt-4 text-sm text-blue-600 hover:text-blue-800 underline"
          >
            Go to chat
          </Link>
        )}
      </div>
    )
  }

  return (
    <div className="space-y-4">
      {kpiItems.length > 0 && (
        <div className="grid grid-cols-1 gap-4">
          {kpiItems.map((item) => (
            <GalleryWidgetCard
              key={`${item.turnId}-${item.widget.id}`}
              item={item}
              extendedInsight={extendedInsights[`${item.turnId}:${item.widget.id}`] ?? extendedInsights[item.widget.id]}
              onExpand={onExpandWidget}
            />
          ))}
        </div>
      )}

      {chartItems.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {chartItems.map((item) => (
            <div key={`${item.turnId}-${item.widget.id}`} className={gridSpanClass(item.widget.type)}>
              <GalleryWidgetCard
                item={item}
                extendedInsight={extendedInsights[`${item.turnId}:${item.widget.id}`] ?? extendedInsights[item.widget.id]}
                onExpand={onExpandWidget}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface GalleryWidgetCardProps {
  item: GalleryWidget
  extendedInsight?: string
  onExpand?: (item: GalleryWidget) => Promise<string | null>
}

function GalleryWidgetCard({ item, extendedInsight, onExpand }: GalleryWidgetCardProps) {
  const { widget } = item
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
      const result = await onExpand(item)
      if (result) {
        setInsight(result)
        setExpanded(true)
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm h-full min-w-0 overflow-hidden">
      <div className="min-w-0">
        <h4 className="font-semibold text-gray-900 text-sm">{widget.title}</h4>
        {widget.description && (
          <p className="text-xs text-gray-500 mt-0.5">{widget.description}</p>
        )}
      </div>
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
