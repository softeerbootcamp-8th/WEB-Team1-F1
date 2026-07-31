import { useEffect, useState } from 'react'
import { Toaster as Sonner, type ToasterProps } from 'sonner'

/**
 * 전역 토스트. 알림(평가 승인/거절, 낙찰, 거래 상태 변경) 노출에 사용.
 * 시스템 테마(.dark)에 맞춰 자동 전환.
 */
function Toaster(props: ToasterProps) {
  const [theme, setTheme] = useState<ToasterProps['theme']>('light')

  useEffect(() => {
    const isDark = document.documentElement.classList.contains('dark')
    setTheme(isDark ? 'dark' : 'light')
  }, [])

  return (
    <Sonner
      theme={theme}
      className="toaster group"
      position="top-center"
      style={
        {
          '--normal-bg': 'var(--popover)',
          '--normal-text': 'var(--popover-foreground)',
          '--normal-border': 'var(--border)',
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
