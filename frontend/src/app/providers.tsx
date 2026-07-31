import { QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'

import { queryClient } from './query-client'
import { AuthProvider } from '@/features/auth/auth-context'
import { Toaster } from '@/components/ui/sonner'

/** 앱 전역 프로바이더 조합: react-query · 라우터 · 인증 · 토스트. */
export function AppProviders({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          {children}
          <Toaster />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
