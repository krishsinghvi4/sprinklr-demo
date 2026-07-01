import { useState } from 'react'
import Markdown from 'react-markdown'
import { ChevronDown, ChevronUp, Sparkles } from 'lucide-react'
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
}: InsightsWidgetGalleryProps) {
  const kpiItems = items.filter((item) => item.widget.type === 'kpi')
  const chartItems = items.filter((item) => item.widget.type !== 'kpi')

  if (items.length === 0) {
    return (
      <p className="text-gray-500 text-center py-8 text-sm">No widgets saved yet</p>
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
  const { widget, turnPrompt } = item
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
    <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm h-full">
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <h4 className="font-semibold text-gray-900 text-sm">{widget.title}</h4>
          {widget.description && (
            <p className="text-xs text-gray-500 mt-0.5">{widget.description}</p>
          )}
        </div>
        <span className="text-[10px] text-gray-400 truncate max-w-[120px] shrink-0" title={turnPrompt}>
          {turnPrompt}
        </span>
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
            <div className="mt-2 prose prose-sm max-w-none text-gray-700">
              <Markdown>{insight}</Markdown>
            </div>
          )}
        </div>
      )}
    </div>
  )
}
