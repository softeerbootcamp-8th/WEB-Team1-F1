import { ShieldCheck } from 'lucide-react'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { EmptyState } from '@/components/common/empty-state'
import { useAuth } from '@/features/auth/auth-context'

/**
 * 관리자 운영 홈. 지금은 딜러 자격 심사가 들어올 자리만 잡아 둔 빈 대시보드다.
 * 이 화면 자체보다 여기까지 오는 길(ADMIN 역할 · 라우트 가드 · /api/admin/** 차단)이 이번 작업의 산출물이다.
 */
export function AdminHomePage() {
  const { user } = useAuth()

  return (
    <main className="bg-muted/30 min-h-full" aria-label="관리자 홈">
      <div className="mx-auto max-w-5xl px-6 py-12">
        <header className="flex flex-wrap items-center gap-3">
          <span className="bg-foreground text-background flex size-11 items-center justify-center rounded-2xl">
            <ShieldCheck className="size-5" />
          </span>
          <div>
            <h1 className="text-2xl font-semibold tracking-tight">운영 관리</h1>
            <p className="text-muted-foreground mt-0.5 text-sm">
              {user?.realName} 관리자님, 환영합니다
            </p>
          </div>
        </header>

        <Card className="mt-8">
          <CardHeader>
            <CardTitle>딜러 자격 심사</CardTitle>
          </CardHeader>
          <CardContent>
            <EmptyState
              title="아직 준비 중입니다"
              description="딜러 신청자가 제출한 자동차매매사원증을 확인하고 승인·반려하는 화면이 이 자리에 들어옵니다."
            />
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
