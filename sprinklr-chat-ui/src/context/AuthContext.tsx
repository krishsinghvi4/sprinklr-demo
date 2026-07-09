import {
  createContext,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'

export interface AuthUser {
  userId: string
  email: string
}

export interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (token: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

interface JwtPayload {
  sub: string
  email: string
  exp?: number
}

function decodeJwtPayload(token: string): JwtPayload {
  const base64Url = token.split('.')[1]
  const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4)
  return JSON.parse(atob(padded))
}

function isTokenExpired(exp?: number): boolean {
  if (exp == null) {
    return false
  }
  return exp * 1000 < Date.now()
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null)
  const [isAuthenticated, setIsAuthenticated] = useState(false)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('chat_token')
    if (token) {
      try {
        const payload = decodeJwtPayload(token)
        if (isTokenExpired(payload.exp)) {
          localStorage.removeItem('chat_token')
        } else {
          setUser({ userId: payload.sub, email: payload.email })
          setIsAuthenticated(true)
        }
      } catch {
        localStorage.removeItem('chat_token')
      }
    }
    setIsLoading(false)
  }, [])

  const login = (token: string) => {
    const payload = decodeJwtPayload(token)
    if (isTokenExpired(payload.exp)) {
      localStorage.removeItem('chat_token')
      window.location.href = '/login'
      return
    }
    localStorage.setItem('chat_token', token)
    setUser({ userId: payload.sub, email: payload.email })
    setIsAuthenticated(true)
  }

  const logout = () => {
    localStorage.removeItem('chat_token')
    setUser(null)
    setIsAuthenticated(false)
    window.location.href = '/login'
  }

  return (
    <AuthContext.Provider
      value={{ user, isAuthenticated, isLoading, login, logout }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
