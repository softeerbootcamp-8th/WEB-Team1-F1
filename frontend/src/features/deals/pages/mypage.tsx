import { Link, useLocation, useNavigate } from 'react-router-dom'
import { LoaderCircle, PackageSearch } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/common/empty-state'
import { ROLE_LABEL, useAuth } from '@/features/auth/auth-context'
import { MyRequestsPanel } from '@/features/evaluations/components/my-requests-panel'
import { DealListPanel } from '../components/deal-list-panel'

/**
 * 탭이 곧 주소다. `/mypage/sales` 처럼 경로에 실어야 상세에서 돌아왔을 때 보던 탭이 유지되고,
 * 방문견적 상세(`/mypage/evaluations/:id`)와 규칙이 같아진다.
 */
const TABS = ['evaluations', 'sales', 'purchases'] as const

type Tab = (typeof TABS)[number]

export function MyPage() {
  const { user, isAuthenticated, isLoading } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  // 모르는 경로가 들어와도 첫 탭으로 떨어뜨린다, 셋 다 아니면 빈 화면이 된다
  const segment = pathname.split('/')[2]
  // `/mypage/deals` 는 기존 북마크를 위해 구매 내역으로 해석한다.
  const tab: Tab = segment === 'deals'
    ? 'purchases'
    : TABS.includes(segment as Tab)
      ? (segment as Tab)
      : 'evaluations'

  if (isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center" aria-label="로그인 확인 중">
        <LoaderCircle className="size-7 animate-spin" />
      </main>
    )
  }

  if (!isAuthenticated || !user) {
    return (
      <main aria-label="마이페이지" className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          icon={PackageSearch}
          title="로그인이 필요합니다"
          description="마이페이지는 로그인 후 이용할 수 있어요."
          action={
            <Button asChild>
              <Link to="/login">로그인</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main aria-label="마이페이지" className="mx-auto max-w-5xl px-6 py-10">
      <header className="mb-8 flex items-center gap-3">
        <h1 className="text-3xl font-semibold md:text-4xl">
          {user.realName}
        </h1>
        <Badge variant="outline">{ROLE_LABEL[user.role]} 회원</Badge>
      </header>

      <Tabs
        value={tab}
        onValueChange={(next) => navigate(`/mypage/${next}`, { replace: true })}
      >
        <TabsList>
          <TabsTrigger value="evaluations">진단 신청 내역</TabsTrigger>
          <TabsTrigger value="sales">판매 내역</TabsTrigger>
          <TabsTrigger value="purchases">구매 내역</TabsTrigger>
        </TabsList>

        <TabsContent value="evaluations" className="mt-6">
          <MyRequestsPanel />
        </TabsContent>

        <TabsContent value="sales" className="mt-6">
          <DealListPanel side="SELLER" enabled={tab === 'sales'} />
        </TabsContent>

        <TabsContent value="purchases" className="mt-6">
          <DealListPanel side="BUYER" enabled={tab === 'purchases'} />
        </TabsContent>
      </Tabs>
    </main>
  )
}
