import { WidgetBlockSchema, type WidgetBlock } from '../types/widgets'

const WIDGET_FENCE_PATTERN = /```widget\s*([\s\S]*?)```/gi

export interface ContentSegment {
  type: 'markdown' | 'html' | 'widget'
  value: string
}

export function parseWidgetPayload(json: string): WidgetBlock | null {
  try {
    const parsed = JSON.parse(json)
    const result = WidgetBlockSchema.safeParse(parsed)
    return result.success ? result.data : null
  } catch {
    return null
  }
}

export function hasWidgetFence(content: string): boolean {
  const pattern = new RegExp(WIDGET_FENCE_PATTERN.source, WIDGET_FENCE_PATTERN.flags)
  return pattern.test(content)
}

export function extractWidgetBlock(content: string): WidgetBlock | null {
  const pattern = new RegExp(WIDGET_FENCE_PATTERN.source, WIDGET_FENCE_PATTERN.flags)
  const match = pattern.exec(content)
  if (!match?.[1]) {
    return null
  }
  return parseWidgetPayload(match[1].trim())
}

export function splitWidgetContent(content: string): ContentSegment[] {
  const segments: ContentSegment[] = []
  let lastIndex = 0
  const pattern = new RegExp(WIDGET_FENCE_PATTERN.source, WIDGET_FENCE_PATTERN.flags)

  for (const match of content.matchAll(pattern)) {
    const matchIndex = match.index ?? 0
    if (matchIndex > lastIndex) {
      const markdown = content.slice(lastIndex, matchIndex).trim()
      if (markdown) {
        segments.push({ type: 'markdown', value: markdown })
      }
    }
    const widgetJson = match[1]?.trim()
    if (widgetJson) {
      segments.push({ type: 'widget', value: widgetJson })
    }
    lastIndex = matchIndex + match[0].length
  }

  const trailing = content.slice(lastIndex).trim()
  if (trailing) {
    segments.push({ type: 'markdown', value: trailing })
  }

  return segments
}
