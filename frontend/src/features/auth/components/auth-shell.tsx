import { Link } from 'react-router-dom'

import { BrandLogo } from '@/components/common/brand-logo'
import { CinematicCarBackdrop } from '@/components/common/cinematic-car-backdrop'

/** 인증 화면 공용 셸 — 좌측 브랜드 패널(블랙) + 우측 폼. */
export function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string
  subtitle?: string
  children: React.ReactNode
  footer: React.ReactNode
}) {
  return (
    <main
      aria-label={title}
      className="grid min-h-svh lg:grid-cols-[minmax(0,1.05fr)_minmax(28rem,0.95fr)]"
    >
      {/* 브랜드 패널 */}
      <aside className="relative isolate flex min-h-[320px] flex-col overflow-hidden bg-[#080a0b] p-6 text-white sm:min-h-[380px] sm:p-8 lg:min-h-0 lg:p-12">
        <CinematicCarBackdrop
          variant="auth"
          className="-z-20"
          imageClassName="object-[62%_center] opacity-80 lg:object-center lg:opacity-85"
          sizes="(min-width: 1024px) 53vw, 100vw"
        />
        <div className="absolute inset-0 -z-10 bg-linear-to-t from-black/90 via-black/25 to-black/50" />
        <Link to="/" aria-label="RACE 홈으로" className="relative w-fit">
          <BrandLogo variant="white" className="h-10" />
        </Link>
        <p className="relative mt-auto hidden text-xs text-white/45 lg:block">
          © 2026 RACE
        </p>
      </aside>

      {/* 폼 */}
      <div className="bg-background flex items-center justify-center px-6 py-14 lg:py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 space-y-1.5">
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            {subtitle && <p className="text-muted-foreground text-sm">{subtitle}</p>}
          </div>
          {children}
          <div className="text-muted-foreground mt-6 text-center text-sm">
            {footer}
          </div>
        </div>
      </div>
    </main>
  )
}
