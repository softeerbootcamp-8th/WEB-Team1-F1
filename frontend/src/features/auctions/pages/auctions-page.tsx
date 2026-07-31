import { useMemo, useState } from 'react'
import { SearchX } from 'lucide-react'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { useAuctionList } from '@/features/auctions/use-auction-list'
import { roomPhaseToStatus } from '@/lib/auction'
import type { AuctionStatus } from '@/types/domain'

type Filter = 'ALL' | AuctionStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'LIVE', label: '진행중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

export function AuctionsPage() {
  const { cards, isLoading, isLoadingMore, hasNext, loadMore } = useAuctionList()
  const [filter, setFilter] = useState<Filter>('ALL')
  const auctions = useMemo(
    () =>
      filter === 'ALL'
        ? cards
        : cards.filter((auction) => roomPhaseToStatus(auction.phase) === filter),
    [cards, filter],
  )

  return (
    <main aria-label="경매 목록" className="mx-auto max-w-7xl px-6 py-12">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div>
          <p className="text-muted-foreground text-sm">LIVE AUCTIONS</p>
          <h1 className="mt-2 text-3xl font-semibold md:text-4xl">경매 목록</h1>
          <p className="text-muted-foreground mt-2">
            평가가 완료된 차량의 실시간 가격 형성 과정을 확인하세요.
          </p>
        </div>
        <Tabs value={filter} onValueChange={(value) => setFilter(value as Filter)}>
          <TabsList>
            {FILTERS.map((item) => (
              <TabsTrigger key={item.value} value={item.value}>
                {item.label}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>
      </header>

      {!isLoading && auctions.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title="해당 상태의 경매가 없습니다"
          description="다른 필터를 선택해 보세요."
        />
      ) : (
        <>
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {auctions.map((auction) => (
              <li key={auction.auctionId}>
                <AuctionCard auction={auction} />
              </li>
            ))}
          </ul>

          {filter === 'ALL' && hasNext && (
            <div className="mt-8 flex justify-center">
              <Button variant="outline" onClick={loadMore} disabled={isLoadingMore}>
                더 보기
              </Button>
            </div>
          )}
        </>
      )}
    </main>
  )
}
