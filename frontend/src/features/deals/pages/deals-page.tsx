import { Link } from 'react-router-dom'
import { PackageSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import { DealListPanel } from '../components/deal-list-panel'

/**
 * 내 거래 목록. 마이페이지 탭과 같은 패널을 쓰지만 주소를 따로 갖는다 —
 * 목록 주소가 마이페이지 화면 구조에 묶이면 그 구조를 손대는 순간 같이 흔들린다.
 */
export function DealsPage() {
  const { isAuthenticated, isLoading } = useAuth()

  if (!isLoading && !isAuthenticated) {
    return (
      <main aria-label="내 거래" className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          icon={PackageSearch}
          title="로그인이 필요합니다"
          description="거래는 로그인 후 확인할 수 있어요."
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
    <main aria-label="내 거래" className="mx-auto max-w-3xl px-6 py-12">
      <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">내 거래</h1>
      <p className="text-muted-foreground mt-2 text-sm">
        낙찰된 거래를 판매·구매 구분 없이 최근 순으로 보여 줍니다.
      </p>

      <div className="mt-8">
        <DealListPanel enabled={isAuthenticated} />
      </div>
    </main>
  )
}
