import { useEffect, useState } from 'react'
import { Copy } from 'lucide-react'
import { fetchTeamsWebhookUrl } from '../services/profileService'

interface TeamsWebhookPanelProps {
  connectionId: string
}

export default function TeamsWebhookPanel({ connectionId }: TeamsWebhookPanelProps) {
  const [webhookUrl, setWebhookUrl] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    let cancelled = false
    const load = async () => {
      setIsLoading(true)
      setError(null)
      try {
        const url = await fetchTeamsWebhookUrl(connectionId)
        if (!cancelled) {
          setWebhookUrl(url)
        }
      } catch (err: unknown) {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load webhook URL.')
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false)
        }
      }
    }
    void load()
    return () => {
      cancelled = true
    }
  }, [connectionId])

  const handleCopy = async () => {
    if (!webhookUrl) return
    try {
      await navigator.clipboard.writeText(webhookUrl)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 2000)
    } catch {
      setError('Could not copy to clipboard.')
    }
  }

  return (
    <div className="mt-3 rounded-lg border border-blue-100 bg-blue-50 p-3">
      <p className="text-xs font-medium text-blue-900">Power Automate webhook URL</p>
      <p className="text-xs text-blue-800 mt-1">
        Paste this URL into each Teams channel workflow HTTP action. Create one workflow per channel.
      </p>
      {isLoading && <p className="text-xs text-blue-700 mt-2">Loading webhook URL...</p>}
      {error && <p className="text-xs text-red-600 mt-2">{error}</p>}
      {webhookUrl && (
        <div className="mt-2 flex items-start gap-2">
          <code className="flex-1 text-xs break-all bg-white border border-blue-200 rounded px-2 py-1 text-gray-800">
            {webhookUrl}
          </code>
          <button
            type="button"
            onClick={() => void handleCopy()}
            className="inline-flex items-center gap-1 px-2 py-1 text-xs border border-blue-200 rounded bg-white hover:bg-blue-100 text-blue-900 shrink-0"
          >
            <Copy className="w-3 h-3" />
            {copied ? 'Copied' : 'Copy'}
          </button>
        </div>
      )}
    </div>
  )
}
