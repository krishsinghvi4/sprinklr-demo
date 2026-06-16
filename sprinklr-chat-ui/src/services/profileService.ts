import axiosInstance from '../api/axiosInstance'
import { ConnectMcpServerRequest, McpConnection, ProfileResponse } from '../types/profile'

export async function fetchProfile(): Promise<ProfileResponse | null> {
  try {
    const response = await axiosInstance.get<ProfileResponse>('/api/v1/profile')
    return response.data
  } catch (error) {
    console.error('[Profile] Failed to fetch profile:', error)
    return null
  }
}

export async function connectMcpServer(
  request: ConnectMcpServerRequest
): Promise<McpConnection | null> {
  const response = await axiosInstance.post<McpConnection>('/api/v1/mcp/connections', request)
  return response.data
}

export async function disconnectMcpServer(connectionId: string): Promise<void> {
  await axiosInstance.delete(`/api/v1/mcp/connections/${connectionId}`)
}

export async function startOAuthConnect(catalogServerId: string): Promise<string> {
  const response = await axiosInstance.get<{ authorizationUrl: string }>(
    `/api/v1/mcp/oauth/start/${catalogServerId}`
  )
  return response.data.authorizationUrl
}
