export interface CredentialField {
  key: string
  label: string
  type: string
  required: boolean
}

export interface AvailableServer {
  id: string
  displayName: string
  description: string
  authType: string
  credentialFields: CredentialField[]
  connected: boolean
}

export interface McpConnection {
  id: string
  catalogServerId: string
  displayName: string
  status: string
  toolCount: number
  connectedAt: string
}

export interface Marketplace {
  availableServers: AvailableServer[]
  connections: McpConnection[]
}

export interface UserProfile {
  userId: string
  username: string
  email: string
  createdAt: string
}

export interface ProfileResponse {
  user: UserProfile
  marketplace: Marketplace
}

export interface ConnectMcpServerRequest {
  catalogServerId: string
  credentials: Record<string, string>
}
