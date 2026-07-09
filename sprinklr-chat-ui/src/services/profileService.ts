import axiosInstance from '../api/axiosInstance'
import {
  ConnectMcpServerRequest,
  McpConnection,
  ProfileResponse,
  RedQueryPreferences,
} from '../types/profile'

export async function fetchProfile(): Promise<ProfileResponse | null> {
  try {
    const response = await axiosInstance.get<ProfileResponse>('/api/v1/profile')
    return response.data
  } catch (error) {
    console.error('[Profile] Failed to fetch profile:', error)
    return null
  }
}

const MCP_REQUEST_TIMEOUT_MS = 30_000

export async function connectMcpServer(
  request: ConnectMcpServerRequest
): Promise<McpConnection | null> {
  const response = await axiosInstance.post<McpConnection>(
    '/api/v1/mcp/connections',
    request,
    { timeout: MCP_REQUEST_TIMEOUT_MS }
  )
  return response.data
}

export async function disconnectMcpServer(connectionId: string): Promise<void> {
  await axiosInstance.delete(`/api/v1/mcp/connections/${connectionId}`)
}

export async function fetchRedQueryPreferences(connectionId: string): Promise<RedQueryPreferences> {
  const response = await axiosInstance.get<RedQueryPreferences>(
    `/api/v1/mcp/connections/${connectionId}/red-query-preferences`
  )
  return response.data
}

export async function updateRedQueryPreferences(
  connectionId: string,
  preferences: RedQueryPreferences
): Promise<RedQueryPreferences> {
  const response = await axiosInstance.put<RedQueryPreferences>(
    `/api/v1/mcp/connections/${connectionId}/red-query-preferences`,
    preferences
  )
  return response.data
}

export async function fetchUserMcpConfigs(): Promise<UserMcpConfigSummary[]> {
  const response = await axiosInstance.get<{ configs: UserMcpConfigSummary[] }>(
    '/api/v1/mcp/user-configs'
  )
  return response.data.configs ?? []
}

export async function createUserMcpConfig(
  request: CreateUserMcpConfigRequest
): Promise<UserMcpConfigSummary> {
  const response = await axiosInstance.post<UserMcpConfigSummary>(
    '/api/v1/mcp/user-configs',
    request
  )
  return response.data
}

export async function deleteUserMcpConfig(configId: string): Promise<void> {
  await axiosInstance.delete(`/api/v1/mcp/user-configs/${configId}`)
}

export async function startOAuthConnect(catalogServerId: string): Promise<string> {
  const response = await axiosInstance.get<{ authorizationUrl: string }>(
    `/api/v1/mcp/oauth/start/${catalogServerId}`,
    { timeout: MCP_REQUEST_TIMEOUT_MS }
  )
  return response.data.authorizationUrl
}

const MCP_OAUTH_CALLBACK_TIMEOUT_MS = 120_000

export async function completeOAuthCallback(
  query: string
): Promise<{ ok: boolean; message?: string }> {
  const headers: Record<string, string> = {}
  const token = localStorage.getItem('chat_token')
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  const controller = new AbortController()
  const timeoutId = window.setTimeout(
    () => controller.abort(),
    MCP_OAUTH_CALLBACK_TIMEOUT_MS
  )

  try {
    const response = await fetch(`/api/v1/mcp/oauth/callback?${query}`, {
      method: 'GET',
      redirect: 'follow',
      credentials: 'include',
      headers,
      signal: controller.signal,
    })

    const finalUrl = new URL(response.url)
    const oauth = finalUrl.searchParams.get('oauth')
    if (oauth === 'success') {
      return { ok: true }
    }
    return {
      ok: false,
      message: finalUrl.searchParams.get('message') || 'OAuth connection failed.',
    }
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return {
        ok: false,
        message: 'Request timed out — check VPN and try again.',
      }
    }
    return {
      ok: false,
      message: 'Network error. Check your connection and try again.',
    }
  } finally {
    window.clearTimeout(timeoutId)
  }
}
