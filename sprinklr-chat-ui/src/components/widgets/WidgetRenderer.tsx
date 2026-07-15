import { useState } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
  Area,
  AreaChart,
} from 'recharts'
import { ChartDataSchema, KpiDataSchema, PieDataSchema, TableDataSchema, TimelineDataSchema, type WidgetSpec } from '../../types/widgets'

const CHART_COLORS = ['#2563eb', '#7c3aed', '#059669', '#d97706', '#dc2626', '#0891b2']

function chartMargins(hasYLabel: boolean) {
  return { top: 8, right: 8, left: hasYLabel ? 16 : 0, bottom: 8 }
}

function axisLabelProps(label: string | undefined, axis: 'x' | 'y') {
  if (!label) return undefined
  if (axis === 'x') {
    return { value: label, position: 'insideBottom' as const, offset: -4, style: { fontSize: 11, fill: '#6b7280' } }
  }
  return { value: label, angle: -90, position: 'insideLeft' as const, style: { fontSize: 11, fill: '#6b7280' } }
}

interface WidgetRendererProps {
  widget: WidgetSpec
}

export default function WidgetRenderer({ widget }: WidgetRendererProps) {
  switch (widget.type) {
    case 'kpi':
      return <KpiCards widget={widget} />
    case 'bar':
      return <BarChartWidget widget={widget} />
    case 'line':
      return <LineChartWidget widget={widget} />
    case 'area':
      return <AreaChartWidget widget={widget} />
    case 'pie':
    case 'donut':
      return <PieChartWidget widget={widget} donut={widget.type === 'donut'} />
    case 'timeline':
      return <TimelineWidget widget={widget} />
    case 'table':
      return <DataTableWidget widget={widget} />
    default:
      return <FallbackWidget widget={widget} />
  }
}

function KpiCards({ widget }: { widget: WidgetSpec }) {
  const parsed = KpiDataSchema.safeParse(widget.data)
  if (!parsed.success) {
    return <FallbackWidget widget={widget} />
  }
  return (
    <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
      {parsed.data.metrics.map((metric) => (
        <div key={metric.label} className="bg-white rounded-lg border border-gray-200 p-4 text-center">
          <p className="text-xs text-gray-500 uppercase tracking-wide">{metric.label}</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">
            {metric.value}
            {metric.unit && <span className="text-sm font-normal text-gray-500 ml-1">{metric.unit}</span>}
          </p>
        </div>
      ))}
    </div>
  )
}

function chartValues(data: { labels: string[]; values: number[] }) {
  return data.labels.map((label, i) => ({
    label,
    value: data.values[i] ?? 0,
  }))
}

function isAllZero(values: number[]) {
  return values.length === 0 || values.every((value) => value === 0)
}

function BarChartWidget({ widget }: { widget: WidgetSpec }) {
  const parsed = ChartDataSchema.safeParse(widget.data)
  if (!parsed.success || isAllZero(parsed.data.values)) {
    return <FallbackWidget widget={widget} />
  }
  const data = chartValues(parsed.data)
  const margins = chartMargins(Boolean(parsed.data.yAxisLabel))
  return (
    <div className="w-full min-w-0 overflow-hidden">
      <ResponsiveContainer width="100%" height={260}>
      <BarChart data={data} margin={margins}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
        <XAxis dataKey="label" tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.xAxisLabel, 'x')} />
        <YAxis tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.yAxisLabel, 'y')} width={parsed.data.yAxisLabel ? 48 : 36} />
        <Tooltip />
        <Bar dataKey="value" fill={CHART_COLORS[0]} radius={[4, 4, 0, 0]} />
      </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function LineChartWidget({ widget }: { widget: WidgetSpec }) {
  const parsed = ChartDataSchema.safeParse(widget.data)
  if (!parsed.success || isAllZero(parsed.data.values)) {
    return <FallbackWidget widget={widget} />
  }
  const data = chartValues(parsed.data)
  const margins = chartMargins(Boolean(parsed.data.yAxisLabel))
  return (
    <div className="w-full min-w-0 overflow-hidden">
      <ResponsiveContainer width="100%" height={260}>
      <LineChart data={data} margin={margins}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
        <XAxis dataKey="label" tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.xAxisLabel, 'x')} />
        <YAxis tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.yAxisLabel, 'y')} width={parsed.data.yAxisLabel ? 48 : 36} />
        <Tooltip />
        <Line type="monotone" dataKey="value" stroke={CHART_COLORS[0]} strokeWidth={2} dot={{ r: 4 }} />
      </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function AreaChartWidget({ widget }: { widget: WidgetSpec }) {
  const parsed = ChartDataSchema.safeParse(widget.data)
  if (!parsed.success || isAllZero(parsed.data.values)) {
    return <FallbackWidget widget={widget} />
  }
  const data = chartValues(parsed.data)
  const margins = chartMargins(Boolean(parsed.data.yAxisLabel))
  return (
    <div className="w-full min-w-0 overflow-hidden">
      <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={data} margin={margins}>
        <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
        <XAxis dataKey="label" tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.xAxisLabel, 'x')} />
        <YAxis tick={{ fontSize: 12 }} label={axisLabelProps(parsed.data.yAxisLabel, 'y')} width={parsed.data.yAxisLabel ? 48 : 36} />
        <Tooltip />
        <Area type="monotone" dataKey="value" stroke={CHART_COLORS[0]} fill={CHART_COLORS[0]} fillOpacity={0.2} />
      </AreaChart>
      </ResponsiveContainer>
    </div>
  )
}

function PieChartWidget({ widget, donut }: { widget: WidgetSpec; donut?: boolean }) {
  const parsed = PieDataSchema.safeParse(widget.data)
  const sliceValues = parsed.success ? parsed.data.slices.map((slice) => slice.value) : []
  if (!parsed.success || isAllZero(sliceValues)) {
    return <FallbackWidget widget={widget} />
  }

  const sliceCount = parsed.data.slices.length
  const manySlices = sliceCount > 5
  const chartHeight = manySlices ? 300 : 260

  return (
    <div className="w-full min-w-0 overflow-hidden">
      <ResponsiveContainer width="100%" height={chartHeight}>
        <PieChart
          margin={
            manySlices
              ? { top: 8, right: 100, bottom: 8, left: 8 }
              : { top: 8, right: 8, bottom: 24, left: 8 }
          }
        >
          <Pie
            data={parsed.data.slices}
            dataKey="value"
            nameKey="label"
            cx={manySlices ? '40%' : '50%'}
            cy="50%"
            innerRadius={donut ? 50 : 0}
            outerRadius={manySlices ? 72 : 90}
            label={
              manySlices
                ? false
                : ({ name, percent }: { name?: string; percent?: number }) =>
                    `${name ?? ''} (${((percent ?? 0) * 100).toFixed(0)}%)`
            }
          >
            {parsed.data.slices.map((_, index) => (
              <Cell key={index} fill={CHART_COLORS[index % CHART_COLORS.length]} />
            ))}
          </Pie>
          <Tooltip />
          <Legend
            layout={manySlices ? 'vertical' : 'horizontal'}
            align={manySlices ? 'right' : 'center'}
            verticalAlign={manySlices ? 'middle' : 'bottom'}
            wrapperStyle={{ fontSize: 11, lineHeight: '14px', maxWidth: manySlices ? 96 : undefined }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  )
}

function TimelineWidget({ widget }: { widget: WidgetSpec }) {
  const parsed = TimelineDataSchema.safeParse(widget.data)
  if (!parsed.success) {
    return <FallbackWidget widget={widget} />
  }
  return (
    <div className="space-y-3">
      {parsed.data.events.map((event, index) => (
        <div key={index} className="flex gap-3">
          <div className="flex flex-col items-center">
            <div className="w-2.5 h-2.5 rounded-full bg-blue-600 mt-1.5" />
            {index < parsed.data.events.length - 1 && (
              <div className="w-px flex-1 bg-gray-200 min-h-[24px]" />
            )}
          </div>
          <div className="pb-3">
            <p className="text-xs text-gray-500">{event.date}{event.author ? ` · ${event.author}` : ''}</p>
            <p className="text-sm text-gray-900">{event.summary}</p>
          </div>
        </div>
      ))}
    </div>
  )
}

function DataTableWidget({ widget }: { widget: WidgetSpec }) {
  const parsed = TableDataSchema.safeParse(widget.data)
  if (!parsed.success) {
    return <FallbackWidget widget={widget} />
  }
  return (
    <div className="overflow-x-auto">
      <table className="min-w-full text-sm border border-gray-200 rounded-lg overflow-hidden">
        <thead className="bg-gray-50">
          <tr>
            {parsed.data.columns.map((col) => (
              <th key={col} className="px-3 py-2 text-left font-medium text-gray-700 border-b">
                {col}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {parsed.data.rows.map((row, rowIndex) => (
            <tr key={rowIndex} className="border-b border-gray-100 hover:bg-gray-50">
              {row.map((cell, cellIndex) => (
                <td key={cellIndex} className="px-3 py-2 text-gray-900">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function FallbackWidget({ widget }: { widget: WidgetSpec }) {
  const [showRaw, setShowRaw] = useState(false)
  return (
    <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
      <p className="text-sm text-amber-800 font-medium">Could not render chart: {widget.title}</p>
      <button
        type="button"
        onClick={() => setShowRaw(!showRaw)}
        className="text-xs text-amber-700 underline mt-2"
      >
        {showRaw ? 'Hide raw data' : 'Show raw data'}
      </button>
      {showRaw && (
        <pre className="mt-2 text-xs overflow-x-auto bg-white p-2 rounded border">
          {JSON.stringify(widget, null, 2)}
        </pre>
      )}
    </div>
  )
}
