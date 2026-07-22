import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'

// Pretendard 가변 폰트 (동적 서브셋 — 필요한 글리프 범위만 로드)
import 'pretendard/dist/web/variable/pretendardvariable-dynamic-subset.css'
import './styles/globals.css'
import { AppProviders } from '@/app/providers'
import { AppRouter } from '@/app/router'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppProviders>
      <AppRouter />
    </AppProviders>
  </StrictMode>,
)
