import { z } from 'zod'

export const KpiDataSchema = z.object({
  metrics: z.array(
    z.object({
      label: z.string(),
      value: z.union([z.string(), z.number()]),
      unit: z.string().optional(),
    }),
  ),
})

export const ChartDataSchema = z
  .object({
    labels: z.array(z.string()),
    values: z.array(z.coerce.number()).optional(),
    xAxisLabel: z.string().optional(),
    yAxisLabel: z.string().optional(),
    series: z
      .array(
        z.object({
          name: z.string(),
          values: z.array(z.coerce.number()),
        }),
      )
      .optional(),
  })
  .transform((data) => {
    if (data.values != null && data.values.length > 0) {
      return { ...data, values: data.values }
    }
    const firstSeries = data.series?.[0]
    if (firstSeries?.values != null && firstSeries.values.length > 0) {
      return { ...data, values: firstSeries.values }
    }
    return { ...data, values: [] as number[] }
  })

export const PieDataSchema = z.object({
  slices: z.array(
    z.object({
      label: z.string(),
      value: z.coerce.number(),
    }),
  ),
})

const TimelineEventInputSchema = z
  .object({
    date: z.string().optional(),
    timestamp: z.string().optional(),
    author: z.string().optional(),
    summary: z.string().optional(),
    label: z.string().optional(),
  })
  .transform((event) => ({
    date: event.date ?? event.timestamp ?? '',
    author: event.author,
    summary: event.summary ?? event.label ?? '',
  }))
  .refine((event) => event.date.length > 0 && event.summary.length > 0, {
    message: 'Timeline events require date (or timestamp) and summary (or label)',
  })

export const TimelineDataSchema = z.object({
  events: z.array(TimelineEventInputSchema),
})

export const TableDataSchema = z.object({
  columns: z.array(z.string()),
  rows: z.array(z.array(z.union([z.string(), z.number()]))),
})

export const WidgetSpecSchema = z.object({
  id: z.string(),
  type: z.enum(['kpi', 'bar', 'line', 'area', 'pie', 'donut', 'timeline', 'table']),
  title: z.string(),
  description: z.string().optional(),
  data: z.record(z.string(), z.unknown()),
})

export const WidgetBlockSchema = z.object({
  version: z.literal(1),
  widgets: z.array(WidgetSpecSchema).min(1),
})

export type WidgetSpec = z.infer<typeof WidgetSpecSchema>
export type WidgetBlock = z.infer<typeof WidgetBlockSchema>
export type KpiData = z.infer<typeof KpiDataSchema>
export type ChartData = z.infer<typeof ChartDataSchema>
export type PieData = z.infer<typeof PieDataSchema>
export type TimelineData = z.infer<typeof TimelineDataSchema>
export type TableData = z.infer<typeof TableDataSchema>
