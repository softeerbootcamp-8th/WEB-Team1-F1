import { LoaderCircle, PackageSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { DealCard } from './deal-card'
import { useDealList } from '../use-deal-list'

/**
 * 내 거래 목록. 마이페이지 탭과 `/deals` 페이지가 같은 것을 쓴다 —
 * 목록이 두 곳에서 보이지만 규칙은 한 벌이어야 한다.
 */
export function DealListPanel({ enabled = true }: { enabled?: boolean }) {
  const { deals, hasNext, isLoading, isLoadingMore, error, loadMoreError, loadMore } =
    useDealList(enabled)

  if (isLoading) {
    return (
      <div className="flex min-h-40 items-center justify-center">
        <LoaderCircle className="size-6 animate-spin" aria-label="거래 목록 불러오는 중" />
      </div>
    )
  }

  if (error) {
    return (
      <EmptyState
        icon={PackageSearch}
        title="거래를 불러오지 못했습니다"
        description={getErrorMessage(error, '잠시 후 다시 시도해 주세요.')}
      />
    )
  }

  if (deals.length === 0) {
    return (
      <EmptyState
        icon={PackageSearch}
        title="진행 중인 거래가 없습니다"
        description="경매에서 낙찰되면 이곳에 거래가 생깁니다."
      />
    )
  }

  return (
    <div className="space-y-4">
      <ul className="space-y-4">
        {deals.map((deal) => (
          <li key={deal.dealId}>
            <DealCard deal={deal} />
          </li>
        ))}
      </ul>

      {/* 이어 읽기 실패는 목록을 지우지 않는다, 이미 읽은 페이지는 그대로 둔다 */}
      {loadMoreError != null && (
        <p className="text-muted-foreground text-center text-sm" role="alert">
          {getErrorMessage(loadMoreError, '다음 목록을 불러오지 못했습니다.')}
        </p>
      )}

      {hasNext && (
        <div className="flex justify-center">
          <Button variant="outline" onClick={loadMore} disabled={isLoadingMore}>
            {isLoadingMore ? '불러오는 중' : '더 보기'}
          </Button>
        </div>
      )}
    </div>
  )
}
