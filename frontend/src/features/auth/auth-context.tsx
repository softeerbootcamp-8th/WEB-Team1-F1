import { createContext, useCallback, useContext, useMemo, useState } from 'react'
import type { User, UserRole } from '@/types/domain'

/**
 * 인증 컨텍스트 (개발용 목업).
 * 실제 연동 시 로그인/회원가입은 Filter/Interceptor 기반 백엔드 API로 교체하고,
 * 세션/JWT는 lib/axios.ts mutator 레이어에서 주입한다.
 */

interface AuthState {
  user: User | null
  isAuthenticated: boolean
  login: (user: User) => void
  logout: () => void
}

const STORAGE_KEY = 'race.auth.user'

const AuthContext = createContext<AuthState | null>(null)

function readStored(): User | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(readStored)

  const login = useCallback((next: User) => {
    setUser(next)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
  }, [])

  const logout = useCallback(() => {
    setUser(null)
    localStorage.removeItem(STORAGE_KEY)
  }, [])

  const value = useMemo<AuthState>(
    () => ({ user, isAuthenticated: !!user, login, logout }),
    [user, login, logout],
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
  USER: '개인',
  DEALER: '딜러',
}
