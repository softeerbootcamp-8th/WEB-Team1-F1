import { Outlet } from 'react-router-dom'

import { Header } from './header'
import { Footer } from './footer'

/** 기본 레이아웃 — 고정 헤더 + 콘텐츠 아울렛 + 푸터. */
export function AppLayout() {
  return (
    <div className="flex min-h-svh flex-col">
      <Header />
      <div className="flex-1">
        <Outlet />
      </div>
      <Footer />
    </div>
  )
}
