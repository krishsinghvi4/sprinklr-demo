import Markdown from 'react-markdown'
import DOMPurify from 'dompurify'
import type { Components } from 'react-markdown'

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
  'strong',
  'span',
  'div',
  'h3',
  'h4',
]

interface ContentSegment {
  type: 'markdown' | 'html'
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
}

export default function AssistantMessageContent({ content }: AssistantMessageContentProps) {
  const segments = splitAssistantContent(content)

  return (
    <div className="space-y-3">
      {segments.map((segment, index) =>
        segment.type === 'html' ? (
          <div
            key={`html-${index}`}
            className="assistant-html"
            dangerouslySetInnerHTML={{ __html: sanitizeAssistantHtml(segment.value) }}
          />
        ) : (
          <Markdown
            key={`md-${index}`}
            className="prose prose-sm max-w-none"
            components={markdownComponents}
          >
            {segment.value}
          </Markdown>
        ),
      )}
    </div>
  )
}
