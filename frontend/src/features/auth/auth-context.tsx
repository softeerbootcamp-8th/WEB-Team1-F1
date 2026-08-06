import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { User, UserRole } from '@/types/domain'
import { fetchMe, loginRequest, logoutRequest, type LoginPayload } from './api'

/**
 * 인증 컨텍스트. 세션은 서버가 HttpOnly 쿠키(RACE_SESSION)로 관리하므로
 * 프론트는 토큰을 들고 있지 않고, 앱 시작 시 /api/auth/me로 로그인 여부만 확인한다.
 */

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  /** 세션 확인(/api/auth/me)이 끝나기 전까지 true. 이 동안은 로그인 여부를 단정하지 않는다. */
  isLoading: boolean
  login: (payload: LoginPayload) => Promise<User>
  logout: () => Promise<void>
  setUser: (user: User) => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)

  useEffect(() => {
    fetchMe()
      .then(setUser)
      .catch(() => setUser(null))
      .finally(() => setIsLoading(false))
  }, [])

  const login = useCallback(async (payload: LoginPayload) => {
    const next = await loginRequest(payload)
    setUser(next)
    return next
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutRequest()
    } finally {
      setUser(null)
      queryClient.clear()
    }
  }, [queryClient])

  const value = useMemo<AuthState>(
    () => ({ user, isAuthenticated: !!user, isLoading, login, logout, setUser }),
    [user, isLoading, login, logout],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>')
  return ctx
}

/** 역할 한글 라벨 */
export const ROLE_LABEL: Record<UserRole, string> = {
  GENERAL: '개인',
  DEALER: '딜러',
  EVALUATOR: '평가사',
}
