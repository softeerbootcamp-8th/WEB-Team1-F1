import { BrandLogo } from '@/components/common/brand-logo'

export function Footer() {
  return (
    <footer className="border-t">
      <div className="text-muted-foreground mx-auto flex max-w-7xl flex-col gap-4 px-6 py-10 text-sm md:flex-row md:items-center md:justify-between">
        <div className="space-y-1">
          <BrandLogo className="h-8" />
          <p className="text-xs">
            평가 기반 중고차 실시간 경매 플랫폼
          </p>
        </div>
        <nav className="flex flex-wrap gap-x-6 gap-y-2 text-xs" aria-label="푸터 메뉴">
          <a href="#" className="hover:text-foreground">이용약관</a>
          <a href="#" className="hover:text-foreground">개인정보처리방침</a>
          <a href="#" className="hover:text-foreground">고객센터</a>
          <span>© 2026 RACE</span>
        </nav>
      </div>
    </footer>
  )
}
