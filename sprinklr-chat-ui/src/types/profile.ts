export type ConnectMethod = 'OAUTH_REDIRECT' | 'CREDENTIAL_FORM' | 'LOCAL_ONLY'

export type ConnectErrorKind =
  | 'VALIDATION_ERROR'
  | 'AUTH_ERROR'
  | 'OAUTH_ERROR'
  | 'MCP_UNAVAILABLE'
  | 'MCP_CIRCUIT_OPEN'
  | 'NETWORK_ERROR'
  | 'UNKNOWN_ERROR'

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
  connectMethod: ConnectMethod
  credentialFields: CredentialField[]
  connected: boolean
  userDefined?: boolean
}

export interface UserMcpConfigSummary {
  id: string
  displayName: string
  description: string
  endpointUrl: string
  serverIdPrefix: string
  headerMode: string
  credentialFields: CredentialField[]
  hasConnectProbe: boolean
  hasSkillText: boolean
  createdAt: string
}

export interface CreateUserMcpConfigRequest {
  displayName: string
  description?: string
  endpointUrl: string
  serverIdPrefix: string
  tokenField: string
  headerMode: 'BEARER' | 'PRIVATE_TOKEN' | 'CUSTOM'
  customHeaderName?: string
  credentialFields?: CredentialField[]
  connectProbe?: {
    tool: string
    arguments?: Record<string, unknown>
    failureMessage: string
  }
  skillText?: string
}

export interface McpConnection {
  id: string
  catalogServerId: string
  displayName: string
  status: string
  toolCount: number
  connectedAt: string
  hasRedQueryPreferences: boolean
}

export interface MongoServerTypeConfig {
  serverType: string
  collectionNames: string[]
}

export interface RedQueryPreferences {
  elasticsearchServerTypes: string[]
  mongoServerTypes: MongoServerTypeConfig[]
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

export interface UsagePeriodSummary {
  totalTokens: number
  promptTokens: number
  completionTokens: number
  estimatedCostUsd: number
  llmCallCount: number
}

export interface UsageSummary {
  allTime: UsagePeriodSummary
  currentMonth: UsagePeriodSummary
}

export interface ProfileResponse {
  user: UserProfile
  marketplace: Marketplace
  usage: UsageSummary
}

export interface ConnectMcpServerRequest {
  catalogServerId: string
  credentials: Record<string, string>
}

export interface ApiErrorResponse {
  message: string
  errorCode?: ConnectErrorKind | string
}

/** Resolves connect UX from backend-driven catalog metadata. */
export function resolveConnectMethod(server: AvailableServer): ConnectMethod {
  if (server.connectMethod) {
    return server.connectMethod
  }
  if (server.authType.startsWith('OAUTH_')) {
    return 'OAUTH_REDIRECT'
  }
  return 'CREDENTIAL_FORM'
}

export function mapApiError(err: unknown): { message: string; kind: ConnectErrorKind } {
  const axiosErr = err as {
    code?: string
    response?: { data?: ApiErrorResponse; status?: number }
  }
  const response = axiosErr?.response
  const message = response?.data?.message || 'Something went wrong. Please try again.'
  const errorCode = response?.data?.errorCode

  if (axiosErr?.code === 'ECONNABORTED') {
    return {
      message: 'Request timed out — check VPN and try again.',
      kind: 'NETWORK_ERROR',
    }
  }

  if (errorCode && isConnectErrorKind(errorCode)) {
    return { message, kind: errorCode }
  }

  const status = response?.status
  if (status === 401 || status === 422) return { message, kind: 'AUTH_ERROR' }
  if (status === 503) return { message, kind: 'MCP_CIRCUIT_OPEN' }
  if (status === 502) return { message, kind: 'MCP_UNAVAILABLE' }
  if (!response) return { message: 'Network error. Check your connection and try again.', kind: 'NETWORK_ERROR' }
  return { message, kind: 'UNKNOWN_ERROR' }
}

function isConnectErrorKind(value: string): value is ConnectErrorKind {
  return [
    'VALIDATION_ERROR',
    'AUTH_ERROR',
    'OAUTH_ERROR',
    'MCP_UNAVAILABLE',
    'MCP_CIRCUIT_OPEN',
    'NETWORK_ERROR',
    'UNKNOWN_ERROR',
  ].includes(value)
}
