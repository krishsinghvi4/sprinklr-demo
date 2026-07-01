import Markdown from 'react-markdown'
import DOMPurify from 'dompurify'
import type { Components } from 'react-markdown'
import WidgetBlock from './widgets/WidgetBlock'
import { splitWidgetContent } from '../utils/parseWidgetPayload'
import type { WidgetSpec } from '../types/widgets'

const HTML_FENCE_PATTERN = /```html\s*([\s\S]*?)```/gi

const ALLOWED_TAGS = [
  'table',
  'thead',
  'tbody',
  'tr',
  'th',
  'td',
  'ul',
  'ol',
  'li',
  'a',
  'p',
  'pre',
  'strong',
  'span',
  'div',
  'h3',
  'h4',
]

const INLINE_HTML_BLOCK_PATTERN =
  /<(?:pre|table|ul|ol|div|h3|h4)\b[^>]*>[\s\S]*?<\/(?:pre|table|ul|ol|div|h3|h4)>/gi

export interface ContentSegment {
  type: 'markdown' | 'html' | 'widget'
  value: string
}

export function splitAssistantContent(content: string): ContentSegment[] {
  const segments: ContentSegment[] = []
  let lastIndex = 0
  const pattern = new RegExp(HTML_FENCE_PATTERN.source, HTML_FENCE_PATTERN.flags)

  for (const match of content.matchAll(pattern)) {
    const matchIndex = match.index ?? 0
    if (matchIndex > lastIndex) {
      const markdown = content.slice(lastIndex, matchIndex).trim()
      if (markdown) {
        segments.push({ type: 'markdown', value: markdown })
      }
    }
    const html = match[1]?.trim()
    if (html) {
      segments.push({ type: 'html', value: html })
    }
    lastIndex = matchIndex + match[0].length
  }

  const trailing = content.slice(lastIndex).trim()
  if (trailing) {
    segments.push({ type: 'markdown', value: trailing })
  }

  if (segments.length === 0 && content.trim()) {
    segments.push({ type: 'markdown', value: content })
  }

  return segments
}

/** Pulls block-level HTML the LLM placed outside ```html fences into renderable segments. */
export function expandMarkdownSegments(segments: ContentSegment[]): ContentSegment[] {
  const expanded: ContentSegment[] = []

  for (const segment of segments) {
    if (segment.type === 'html') {
      expanded.push(segment)
      continue
    }

    const inlinePattern = new RegExp(INLINE_HTML_BLOCK_PATTERN.source, INLINE_HTML_BLOCK_PATTERN.flags)
    let lastIndex = 0
    let foundInline = false

    for (const match of segment.value.matchAll(inlinePattern)) {
      foundInline = true
      const matchIndex = match.index ?? 0
      if (matchIndex > lastIndex) {
        const markdown = segment.value.slice(lastIndex, matchIndex).trim()
        if (markdown) {
          expanded.push({ type: 'markdown', value: markdown })
        }
      }
      const html = match[0]?.trim()
      if (html) {
        expanded.push({ type: 'html', value: html })
      }
      lastIndex = matchIndex + match[0].length
    }

    if (!foundInline) {
      expanded.push(segment)
      continue
    }

    const trailing = segment.value.slice(lastIndex).trim()
    if (trailing) {
      expanded.push({ type: 'markdown', value: trailing })
    }
  }

  return expanded
}

export function parseAssistantContent(content: string): ContentSegment[] {
  const htmlSegments = expandMarkdownSegments(splitAssistantContent(content))
  const result: ContentSegment[] = []

  for (const segment of htmlSegments) {
    if (segment.type !== 'markdown') {
      result.push(segment)
      continue
    }
    const widgetSegments = splitWidgetContent(segment.value)
    for (const ws of widgetSegments) {
      result.push(ws)
    }
  }

  return result
}

const ALLOWED_ATTR = ['href', 'target', 'rel']

DOMPurify.addHook('afterSanitizeAttributes', (node: Element) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

export function sanitizeAssistantHtml(html: string): string {
  return DOMPurify.sanitize(html, { ALLOWED_TAGS, ADD_ATTR: ALLOWED_ATTR })
}

const markdownComponents: Components = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
}

interface AssistantMessageContentProps {
  content: string
  mode?: 'chat' | 'dashboard'
  onSaveToDashboard?: () => void
  onExpandWidget?: (widget: WidgetSpec) => Promise<string | null>
  saveDisabled?: boolean
  isAlreadySaved?: boolean
  dashboardLink?: string
}

export default function AssistantMessageContent({
  content,
  mode = 'chat',
  onSaveToDashboard,
  onExpandWidget,
  saveDisabled = false,
  isAlreadySaved = false,
  dashboardLink,
}: AssistantMessageContentProps) {
  const segments = parseAssistantContent(content)

  return (
    <div className="space-y-3">
      {segments.map((segment, index) => {
        if (segment.type === 'html') {
          return (
            <div
              key={`html-${index}`}
              className="assistant-html"
              dangerouslySetInnerHTML={{ __html: sanitizeAssistantHtml(segment.value) }}
            />
          )
        }
        if (segment.type === 'widget') {
          return (
            <WidgetBlock
              key={`widget-${index}`}
              widgetJson={segment.value}
              mode={mode}
              onSaveToDashboard={onSaveToDashboard}
              onExpandWidget={onExpandWidget}
              saveDisabled={saveDisabled}
              isAlreadySaved={isAlreadySaved}
              dashboardLink={dashboardLink}
            />
          )
        }
        return (
          <Markdown
            key={`md-${index}`}
            className="prose prose-sm max-w-none"
            components={markdownComponents}
          >
            {segment.value}
          </Markdown>
        )
      })}
    </div>
  )
}
