import { useEffect, useState } from 'react'
import { Plug, PlugZap, Unplug } from 'lucide-react'
import AppHeader from '../components/AppHeader'
import McpConnectModal from '../components/McpConnectModal'
import {
  connectMcpServer,
  disconnectMcpServer,
  fetchProfile,
  startOAuthConnect,
} from '../services/profileService'
import { AvailableServer, ProfileResponse } from '../types/profile'

export default function ProfilePage() {
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [connectingServer, setConnectingServer] = useState<AvailableServer | null>(null)
  const [actionMessage, setActionMessage] = useState<string | null>(null)

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
      setActionMessage('Connected to Jira via OAuth.')
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

  const handleConnect = async (credentials: Record<string, string>) => {
    if (!connectingServer) return
    await connectMcpServer({
      catalogServerId: connectingServer.id,
      credentials,
    })
    setActionMessage(`Connected to ${connectingServer.displayName}`)
    await loadProfile()
  }

  const handleOAuthConnect = async (server: AvailableServer) => {
    try {
      setError(null)
      const authorizationUrl = await startOAuthConnect(server.id)
      window.location.href = authorizationUrl
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        || 'Failed to start OAuth connection. Please try again.'
      setError(message)
    }
  }

  const handleDisconnect = async (connectionId: string, displayName: string) => {
    try {
      await disconnectMcpServer(connectionId)
      setActionMessage(`Disconnected from ${displayName}`)
      await loadProfile()
    } catch {
      setError('Failed to disconnect server.')
    }
  }

  const getConnectionForServer = (serverId: string) =>
    profile?.marketplace.connections.find((c) => c.catalogServerId === serverId)

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
              <h2 className="text-lg font-semibold text-gray-900 mb-1">MCP Marketplace</h2>
              <p className="text-sm text-gray-500 mb-4">
                Connect MCP servers to use their tools in chat.
              </p>

              {profile.marketplace.availableServers.length === 0 ? (
                <p className="text-sm text-gray-500">No MCP servers available.</p>
              ) : (
                <ul className="space-y-3">
                  {profile.marketplace.availableServers.map((server) => {
                    const connection = getConnectionForServer(server.id)
                    const isConnected = server.connected && connection

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
                          </div>
                        </div>

                        <div className="shrink-0">
                          {isConnected && connection ? (
                            <button
                              onClick={() =>
                                handleDisconnect(connection.id, server.displayName)
                              }
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 text-sm text-red-600 border border-red-200 rounded-lg hover:bg-red-50"
                            >
                              <Unplug className="w-3.5 h-3.5" />
                              Disconnect
                            </button>
                          ) : (
                            <button
                              onClick={() => {
                                if (server.authType === 'OAUTH_ATLASSIAN') {
                                  handleOAuthConnect(server)
                                  return
                                }
                                setConnectingServer(server)
                              }}
                              className="px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                            >
                              Connect
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
