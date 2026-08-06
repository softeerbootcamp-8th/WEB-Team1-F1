import { Link } from 'react-router-dom'

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
    <main aria-label={title} className="grid min-h-svh lg:grid-cols-2">
      {/* 브랜드 패널 */}
      <aside className="bg-foreground text-background relative hidden flex-col justify-between p-12 lg:flex">
        <Link to="/" className="text-lg font-semibold tracking-[0.06em]">
          RACE
        </Link>
        <div>
          <h2 className="text-3xl leading-tight font-semibold tracking-[-0.03em] text-balance">
            개인도 딜러도 안심하고 참여하는
            <br />
            실시간 중고차 라이브 경매
          </h2>
          <p className="text-background/60 mt-4 max-w-sm text-sm">
            누구나 사고팔 수 있는 투명한 경매. 시세 조회부터 낙찰 후 거래
            추적까지 한 곳에서.
          </p>
        </div>
        <p className="text-background/40 text-xs">© 2026 RACE</p>
      </aside>

      {/* 폼 */}
      <div className="flex items-center justify-center px-6 py-12">
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
