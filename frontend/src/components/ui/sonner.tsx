import { useEffect, useState } from 'react'
import { Toaster as Sonner, type ToasterProps } from 'sonner'

import { cn } from '@/lib/utils'

/** 일반 토스트와 서버 알림 큐가 함께 쓰는 렌더러. 시스템 테마를 동일하게 따른다. */
function Toaster({ className, style, ...props }: ToasterProps) {
  const [theme, setTheme] = useState<ToasterProps['theme']>('light')

  useEffect(() => {
    const isDark = document.documentElement.classList.contains('dark')
    setTheme(isDark ? 'dark' : 'light')
  }, [])

  return (
    <Sonner
      theme={theme}
      className={cn('toaster group', className)}
      position="top-center"
      style={
        {
          '--normal-bg': 'var(--popover)',
          '--normal-text': 'var(--popover-foreground)',
          '--normal-border': 'var(--border)',
          ...style,
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
