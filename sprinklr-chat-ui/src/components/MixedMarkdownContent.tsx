import Markdown from 'react-markdown'
import type { Components } from 'react-markdown'
import {
  expandMarkdownSegments,
  sanitizeAssistantHtml,
  splitAssistantContent,
} from './AssistantMessageContent'

const markdownComponents: Components = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
}

interface MixedMarkdownContentProps {
  content: string
  className?: string
}

/** Renders markdown mixed with ```html fences or inline block HTML (tables, lists, etc.). */
export default function MixedMarkdownContent({ content, className }: MixedMarkdownContentProps) {
  const segments = expandMarkdownSegments(splitAssistantContent(content))

  return (
    <div className={className}>
      {segments.map((segment, index) => {
        if (segment.type === 'html') {
          return (
            <div
              key={`html-${index}`}
              className="assistant-html overflow-x-auto"
              dangerouslySetInnerHTML={{ __html: sanitizeAssistantHtml(segment.value) }}
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
