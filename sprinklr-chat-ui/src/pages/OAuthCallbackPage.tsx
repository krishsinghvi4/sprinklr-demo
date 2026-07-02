import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'

export default function OAuthCallbackPage() {
  const [searchParams] = useSearchParams()

  useEffect(() => {
    const query = searchParams.toString()

    if (window.opener && !window.opener.closed) {
      try {
        window.opener.sessionStorage.setItem('mcp_oauth_completing', '1')
      } catch {
        // ignore storage errors
      }
      window.opener.postMessage(
        { type: 'mcp-oauth-callback', query },
        window.location.origin
      )
      window.close()
      return
    }

    window.location.replace(`/api/v1/mcp/oauth/callback${query ? `?${query}` : ''}`)
  }, [searchParams])

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <p className="text-sm text-gray-600">Completing OAuth connection...</p>
    </div>
  )
}
