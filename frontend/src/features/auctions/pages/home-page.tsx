import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, SearchX } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { MOCK_AUCTIONS } from '@/features/auctions/mock'
import type { AuctionStatus } from '@/types/domain'

type Filter = 'ALL' | AuctionStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'LIVE', label: '진행중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

export function HomePage() {
  const [filter, setFilter] = useState<Filter>('ALL')

  const auctions = useMemo(() => {
    if (filter === 'ALL') return MOCK_AUCTIONS
    return MOCK_AUCTIONS.filter((a) => a.status === filter)
  }, [filter])

  const liveCount = MOCK_AUCTIONS.filter((a) => a.status === 'LIVE').length

  return (
    <main aria-label="홈 - 경매 목록">
      {/* Hero — 블랙 배경, 넓은 여백, 얇은 헤드라인 (Kia 톤) */}
      <section className="bg-foreground text-background relative overflow-hidden">
        <div className="mx-auto max-w-7xl px-6 py-20 md:py-28">
          <p className="text-background/60 mb-4 text-sm font-medium tracking-[0.2em] uppercase">
            Real Time Auction Car Exchange
          </p>
          <h1 className="max-w-3xl text-4xl leading-[1.1] font-semibold tracking-[-0.03em] text-balance md:text-6xl">
            개인도 딜러도 안심하고 참여하는,
            <br />
            실시간 중고차 라이브 경매
          </h1>
          <p className="text-background/70 mt-6 max-w-xl text-base md:text-lg">
            누구나 사고팔 수 있는 투명한 중고차 경매. 시세 조회부터 실시간 입찰,
            낙찰 후 거래 추적까지 한 곳에서.
          </p>
          <div className="mt-9 flex flex-wrap items-center gap-3">
            <Button size="xl" variant="secondary" asChild>
              <Link to="/sell">
                내 차 팔기
                <ArrowRight className="size-4" />
              </Link>
            </Button>
            <div className="text-background/70 flex items-center gap-2 text-sm">
              <span className="bg-status-live inline-block size-2 rounded-full" style={{ animation: 'var(--animate-live-pulse)' }} />
              지금 <span className="text-background font-semibold">{liveCount}건</span> 경매 진행중
            </div>
          </div>
        </div>
      </section>

      {/* 목록 */}
      <section className="mx-auto max-w-7xl px-6 py-12">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h2 className="text-2xl font-semibold tracking-tight">경매 매물</h2>
            <p className="text-muted-foreground mt-1 text-sm">
              진행중·예정·종료된 경매를 확인하세요.
            </p>
          </div>
          <Tabs value={filter} onValueChange={(v) => setFilter(v as Filter)}>
            <TabsList>
              {FILTERS.map((f) => (
                <TabsTrigger key={f.value} value={f.value}>
                  {f.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>

        {auctions.length === 0 ? (
          <EmptyState
            icon={SearchX}
            title="해당 상태의 경매가 없습니다"
            description="다른 필터를 선택해 보세요."
          />
        ) : (
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {auctions.map((auction) => (
              <li key={auction.id}>
                <AuctionCard auction={auction} />
              </li>
            ))}
          </ul>
        )}
      </section>
    </main>
  )
}
