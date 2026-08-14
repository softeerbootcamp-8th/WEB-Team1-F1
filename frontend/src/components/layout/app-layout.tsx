import { Outlet, useLocation } from 'react-router-dom'

import { Header } from './header'
import { Footer } from './footer'
import { useAuth } from '@/features/auth/auth-context'

/** 기본 레이아웃 — 고정 헤더 + 콘텐츠 아울렛 + 푸터. */
export function AppLayout() {
  const { isLoading } = useAuth()
  const { pathname } = useLocation()
  const isViewportOnlyPage = pathname === '/sell' || pathname === '/quote'

  // 세션 확인(/api/auth/me) 전에 그리면 로그인 상태를 오판한 화면이 잠깐 보인다.
  if (isLoading) {
    return <div className="min-h-svh" />
  }

  return (
    <div className="flex min-h-svh flex-col">
      <Header />
      <div className="min-h-0 flex-1">
        <Outlet />
      </div>
      {!isViewportOnlyPage && <Footer />}
    </div>
  )
}
