import { LoaderCircle, PackageSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { DealCard } from './deal-card'
import { useDealList } from '../use-deal-list'
import type { DealSide } from '../types'

/**
 * 내 거래 목록. 마이페이지 탭과 `/deals` 페이지가 같은 것을 쓴다 —
 * 목록이 두 곳에서 보이지만 규칙은 한 벌이어야 한다.
 */
export function DealListPanel({
  enabled = true,
  side,
}: {
  enabled?: boolean
  side?: DealSide
}) {
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

  const visibleDeals = side ? deals.filter((deal) => deal.mySide === side) : deals

  if (deals.length === 0) {
    return (
      <EmptyState
        icon={PackageSearch}
        title={side === 'SELLER' ? '판매 내역이 없습니다' : '구매 내역이 없습니다'}
        description={
          side === 'SELLER'
            ? '등록한 차량이 낙찰되면 이곳에 판매 거래가 생깁니다.'
            : '경매에서 낙찰받으면 이곳에 구매 거래가 생깁니다.'
        }
      />
    )
  }

  if (visibleDeals.length === 0) {
    return (
      <EmptyState
        icon={PackageSearch}
        title={side === 'SELLER' ? '불러온 판매 내역이 없습니다' : '불러온 구매 내역이 없습니다'}
        description={hasNext ? '다음 내역에 해당 거래가 있을 수 있습니다.' : '다른 내역을 확인해 보세요.'}
        action={
          hasNext ? (
            <Button onClick={loadMore} disabled={isLoadingMore}>
              {isLoadingMore ? '불러오는 중' : '다음 내역 더 보기'}
            </Button>
          ) : undefined
        }
      />
    )
  }

  return (
    <div className="space-y-4">
      <ul className="space-y-4">
        {visibleDeals.map((deal) => (
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
