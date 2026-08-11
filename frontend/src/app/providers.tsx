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
          <Toaster
            closeButton
            toastOptions={{ closeButtonAriaLabel: '토스트 닫기' }}
          />
          <Toaster
            id="server-notifications"
            className="notification-toaster"
            position="top-right"
            expand
            visibleToasts={3}
            offset={{ top: '5rem', right: '1.5rem' }}
            mobileOffset={{ top: '4.5rem', right: '1rem', left: '1rem' }}
            swipeDirections={['right']}
            containerAriaLabel="서버 알림"
            style={
              {
                '--width': 'min(27rem, calc(100vw - 2rem))',
              } as React.CSSProperties
            }
          />
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  )
}
