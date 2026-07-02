import { useEffect, useRef, useState } from 'react'
import { Plug, PlugZap, Unplug } from 'lucide-react'
import AppHeader from '../components/AppHeader'
import McpConnectModal from '../components/McpConnectModal'
import {
  completeOAuthCallback,
  connectMcpServer,
  disconnectMcpServer,
  fetchProfile,
  startOAuthConnect,
} from '../services/profileService'
import {
  AvailableServer,
  ConnectErrorKind,
  ProfileResponse,
  mapApiError,
  resolveConnectMethod,
} from '../types/profile'
import { formatCostUsd, formatTokenCount } from '../utils/formatUsage'

type ServerActionPhase = 'idle' | 'connecting' | 'disconnecting' | 'redirecting_oauth'

const MCP_CONNECT_SAFETY_TIMEOUT_MS = 35_000
const MCP_OAUTH_CALLBACK_SAFETY_TIMEOUT_MS = 120_000
const OAUTH_POPUP_NAME = 'mcp-oauth'

interface ServerActionState {
  phase: ServerActionPhase
  error?: { kind: ConnectErrorKind; message: string }
}

export default function ProfilePage() {
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [connectingServer, setConnectingServer] = useState<AvailableServer | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)
  const [serverActions, setServerActions] = useState<Record<string, ServerActionState>>({})
  const oauthPopupRef = useRef<Window | null>(null)
  const oauthPollRef = useRef<number | null>(null)
  const pendingOAuthServerRef = useRef<string | null>(null)
  const oauthCallbackInFlightRef = useRef(false)

  const setServerAction = (serverId: string, state: ServerActionState) => {
    setServerActions((prev) => ({ ...prev, [serverId]: state }))
  }

  const clearServerAction = (serverId: string) => {
    setServerActions((prev) => {
      const next = { ...prev }
      delete next[serverId]
      return next
    })
  }

  const loadProfile = async () => {
    setIsLoading(true)
    setError(null)
    const data = await fetchProfile()
    if (!data) {
      setError('Failed to load profile.')
    } else {
      setProfile(data)
    }
    setIsLoading(false)
  }

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const code = params.get('code')
    const state = params.get('state')
    const providerError = params.get('error')

    if (code && state) {
      window.location.replace(
        `/api/v1/mcp/oauth/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`
      )
      return
    }

    if (providerError) {
      window.location.replace(
        `/api/v1/mcp/oauth/callback?error=${encodeURIComponent(providerError)}`
      )
      return
    }

    const oauthStatus = params.get('oauth')
    if (oauthStatus === 'success') {
      setActionMessage('MCP server connected successfully via OAuth.')
      params.delete('oauth')
      const nextUrl = `${window.location.pathname}${params.toString() ? `?${params}` : ''}`
      window.history.replaceState({}, '', nextUrl)
      void loadProfile()
      return
    }

    if (oauthStatus === 'error') {
      const message = params.get('message')
      setError(message || 'OAuth connection failed. Please try again.')
      params.delete('oauth')
      params.delete('message')
      const nextUrl = `${window.location.pathname}${params.toString() ? `?${params}` : ''}`
      window.history.replaceState({}, '', nextUrl)
      void loadProfile()
      return
    }

    void loadProfile()
  }, [])

  useEffect(() => {
    return () => {
      if (oauthPollRef.current != null) {
        window.clearInterval(oauthPollRef.current)
      }
    }
  }, [])

  const finishOAuthCallback = async (serverId: string, query: string) => {
    if (oauthCallbackInFlightRef.current) return
    oauthCallbackInFlightRef.current = true
    setServerAction(serverId, { phase: 'connecting' })
    try {
      const result = await completeOAuthCallback(query)
      if (result.ok) {
        setActionMessage('MCP server connected successfully via OAuth.')
        clearServerAction(serverId)
        await loadProfile()
      } else {
        setServerAction(serverId, {
          phase: 'idle',
          error: { kind: 'OAUTH_ERROR', message: result.message || 'OAuth connection failed.' },
        })
        setError(result.message || 'OAuth connection failed.')
      }
    } finally {
      oauthCallbackInFlightRef.current = false
      pendingOAuthServerRef.current = null
    }
  }

  useEffect(() => {
    const onMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      if (event.data?.type !== 'mcp-oauth-callback') return
      const serverId = pendingOAuthServerRef.current
      if (!serverId || typeof event.data.query !== 'string') return
      void finishOAuthCallback(serverId, event.data.query)
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])

  useEffect(() => {
    const pending = Object.entries(serverActions).find(
      ([, action]) => action.phase === 'connecting' || action.phase === 'redirecting_oauth'
    )
    if (!pending) return

    const [serverId, action] = pending
    const timeoutMs = action.phase === 'connecting'
      ? MCP_OAUTH_CALLBACK_SAFETY_TIMEOUT_MS
      : MCP_CONNECT_SAFETY_TIMEOUT_MS
    const timer = window.setTimeout(() => {
      setServerActions((prev) => {
        const current = prev[serverId]
        if (
          !current
          || (current.phase !== 'connecting' && current.phase !== 'redirecting_oauth')
        ) {
          return prev
        }
        return {
          ...prev,
          [serverId]: {
            phase: 'idle',
            error: {
              kind: 'NETWORK_ERROR',
              message: 'Request timed out — check VPN and try again.',
            },
          },
        }
      })
    }, timeoutMs)

    return () => window.clearTimeout(timer)
  }, [serverActions])

  const handleConnect = async (credentials: Record<string, string>) => {
    if (!connectingServer) return
    setServerAction(connectingServer.id, { phase: 'connecting' })
    try {
      await connectMcpServer({
        catalogServerId: connectingServer.id,
        credentials,
      })
      setActionMessage(`Connected to ${connectingServer.displayName}`)
      clearServerAction(connectingServer.id)
      await loadProfile()
    } catch (err: unknown) {
      const mapped = mapApiError(err)
      setServerAction(connectingServer.id, {
        phase: 'idle',
        error: mapped,
      })
      throw err
    }
  }

  const handleOAuthConnect = async (server: AvailableServer) => {
    if (isAnyServerBusy()) return
    setError(null)
    setActionMessage(null)
    pendingOAuthServerRef.current = server.id
    setServerAction(server.id, { phase: 'connecting' })
    try {
      const authorizationUrl = await startOAuthConnect(server.id)
      setServerAction(server.id, { phase: 'redirecting_oauth' })
      const popup = window.open(
        authorizationUrl,
        OAUTH_POPUP_NAME,
        'width=600,height=700,menubar=no,toolbar=no,location=yes,status=no'
      )
      if (!popup) {
        setServerAction(server.id, {
          phase: 'idle',
          error: {
            kind: 'OAUTH_ERROR',
            message: 'Popup blocked. Allow popups for this site and try again.',
          },
        })
        return
      }
      oauthPopupRef.current = popup
      if (oauthPollRef.current != null) {
        window.clearInterval(oauthPollRef.current)
      }
      oauthPollRef.current = window.setInterval(() => {
        if (popup.closed) {
          if (oauthPollRef.current != null) {
            window.clearInterval(oauthPollRef.current)
            oauthPollRef.current = null
          }
          oauthPopupRef.current = null
          const completing = sessionStorage.getItem('mcp_oauth_completing') === '1'
          if (completing) {
            sessionStorage.removeItem('mcp_oauth_completing')
          }
          if (completing) {
            setServerAction(server.id, { phase: 'connecting' })
          } else {
            setServerActions((prev) => {
              const current = prev[server.id]
              if (!current || current.phase !== 'redirecting_oauth') {
                return prev
              }
              return {
                ...prev,
                [server.id]: { phase: 'idle' },
              }
            })
            pendingOAuthServerRef.current = null
            setError('OAuth was cancelled.')
          }
        }
      }, 500)
    } catch (err: unknown) {
      const mapped = mapApiError(err)
      setServerAction(server.id, { phase: 'idle', error: mapped })
      setError(mapped.message)
    }
  }

  const handleDisconnect = async (serverId: string, connectionId: string, displayName: string) => {
    setServerAction(serverId, { phase: 'disconnecting' })
    try {
      await disconnectMcpServer(connectionId)
      setActionMessage(`Disconnected from ${displayName}`)
      clearServerAction(serverId)
      await loadProfile()
    } catch (err: unknown) {
      const mapped = mapApiError(err)
      setError(mapped.message)
      clearServerAction(serverId)
    }
  }

  const isAnyServerBusy = () =>
    Object.values(serverActions).some(
      (action) =>
        action.phase === 'connecting'
        || action.phase === 'redirecting_oauth'
        || action.phase === 'disconnecting'
    )

  const handleConnectClick = (server: AvailableServer) => {
    if (isAnyServerBusy()) return
    const method = resolveConnectMethod(server)
    if (method === 'OAUTH_REDIRECT') {
      void handleOAuthConnect(server)
      return
    }
    if (server.credentialFields.length === 0) {
      setError(`Connect is not configured for ${server.displayName}.`)
      return
    }
    setConnectingServer(server)
  }

  const getConnectionForServer = (serverId: string) =>
    profile?.marketplace.connections.find((c) => c.catalogServerId === serverId)

  const getConnectButtonLabel = (serverId: string) => {
    const action = serverActions[serverId]
    if (!action) return 'Connect'
    if (action.phase === 'redirecting_oauth') return 'Redirecting...'
    if (action.phase === 'connecting') return 'Connecting...'
    return 'Connect'
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-50">
      <AppHeader title="Profile" backLink={{ to: '/', label: '← Back to chats' }} />

      <main className="flex-1 max-w-3xl w-full mx-auto px-4 py-6 space-y-6">
        {actionMessage && (
          <div className="bg-green-50 border border-green-200 text-green-800 text-sm rounded-lg px-4 py-3">
            {actionMessage}
          </div>
        )}

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-800 text-sm rounded-lg px-4 py-3">
            {error}
          </div>
        )}

        {isLoading ? (
          <p className="text-gray-500 text-sm">Loading profile...</p>
        ) : profile ? (
          <>
            <section className="bg-white rounded-lg border border-gray-200 shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-4">Account</h2>
              <dl className="space-y-3 text-sm">
                <div className="flex justify-between">
                  <dt className="text-gray-500">Username</dt>
                  <dd className="text-gray-900 font-medium">{profile.user.username}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Email</dt>
                  <dd className="text-gray-900 font-medium">{profile.user.email}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-gray-500">Member since</dt>
                  <dd className="text-gray-900">
                    {new Date(profile.user.createdAt).toLocaleDateString()}
                  </dd>
                </div>
              </dl>
            </section>

            <section className="bg-white rounded-lg border border-gray-200 shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-1">LLM Usage</h2>
              <p className="text-sm text-gray-500 mb-4">
                Estimated token usage and cost from your chat sessions.
              </p>
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="text-left text-gray-500 border-b border-gray-100">
                      <th className="pb-2 font-medium">Period</th>
                      <th className="pb-2 font-medium text-right">Tokens</th>
                      <th className="pb-2 font-medium text-right">Est. cost</th>
                      <th className="pb-2 font-medium text-right">LLM calls</th>
                    </tr>
                  </thead>
                  <tbody className="text-gray-900">
                    <tr className="border-b border-gray-50">
                      <td className="py-2.5 text-gray-600">All time</td>
                      <td className="py-2.5 text-right font-medium">
                        {formatTokenCount(profile.usage.allTime.totalTokens)}
                      </td>
                      <td className="py-2.5 text-right font-medium">
                        {formatCostUsd(profile.usage.allTime.estimatedCostUsd)}
                      </td>
                      <td className="py-2.5 text-right text-gray-600">
                        {profile.usage.allTime.llmCallCount.toLocaleString()}
                      </td>
                    </tr>
                    <tr>
                      <td className="py-2.5 text-gray-600">This month</td>
                      <td className="py-2.5 text-right font-medium">
                        {formatTokenCount(profile.usage.currentMonth.totalTokens)}
                      </td>
                      <td className="py-2.5 text-right font-medium">
                        {formatCostUsd(profile.usage.currentMonth.estimatedCostUsd)}
                      </td>
                      <td className="py-2.5 text-right text-gray-600">
                        {profile.usage.currentMonth.llmCallCount.toLocaleString()}
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <section className="bg-white rounded-lg border border-gray-200 shadow-sm p-6">
              <h2 className="text-lg font-semibold text-gray-900 mb-1">MCP Marketplace</h2>
              <p className="text-sm text-gray-500 mb-4">
                Connect MCP servers to use their tools in chat.
              </p>
              <div className="bg-amber-50 border border-amber-200 text-amber-800 text-sm rounded-lg px-4 py-3 mb-4">
                Make sure you are connected to the company VPN before connecting MCP servers.
              </div>

              {profile.marketplace.availableServers.length === 0 ? (
                <p className="text-sm text-gray-500">No MCP servers available.</p>
              ) : (
                <ul className="space-y-3">
                  {profile.marketplace.availableServers.map((server) => {
                    const connection = getConnectionForServer(server.id)
                    const isConnected = server.connected && connection
                    const serverAction = serverActions[server.id]
                    const anyBusy = isAnyServerBusy()
                    const isBusy = anyBusy
                      || serverAction?.phase === 'connecting'
                      || serverAction?.phase === 'disconnecting'
                      || serverAction?.phase === 'redirecting_oauth'

                    return (
                      <li
                        key={server.id}
                        className="border border-gray-200 rounded-lg p-4 flex justify-between items-start gap-4"
                      >
                        <div className="flex gap-3">
                          {isConnected ? (
                            <PlugZap className="w-5 h-5 text-green-600 shrink-0 mt-0.5" />
                          ) : (
                            <Plug className="w-5 h-5 text-gray-400 shrink-0 mt-0.5" />
                          )}
                          <div>
                            <h3 className="text-sm font-medium text-gray-900">
                              {server.displayName}
                            </h3>
                            <p className="text-xs text-gray-500 mt-1">{server.description}</p>
                            {isConnected && connection && (
                              <p className="text-xs text-green-700 mt-2">
                                Connected — {connection.toolCount} tools available
                              </p>
                            )}
                            {serverAction?.error && (
                              <p className="text-xs text-red-600 mt-2">{serverAction.error.message}</p>
                            )}
                          </div>
                        </div>

                        <div className="shrink-0">
                          {isConnected && connection ? (
                            <button
                              onClick={() =>
                                handleDisconnect(server.id, connection.id, server.displayName)
                              }
                              disabled={isBusy}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50 disabled:opacity-50"
                            >
                              <Unplug className="w-3.5 h-3.5" />
                              {serverAction?.phase === 'disconnecting' ? 'Disconnecting...' : 'Disconnect'}
                            </button>
                          ) : (
                            <button
                              onClick={() => handleConnectClick(server)}
                              disabled={isBusy}
                              className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                            >
                              {getConnectButtonLabel(server.id)}
                            </button>
                          )}
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          </>
        ) : null}
      </main>

      {connectingServer && (
        <McpConnectModal
          server={connectingServer}
          onClose={() => setConnectingServer(null)}
          onConnect={handleConnect}
        />
      )}
    </div>
  )
}
