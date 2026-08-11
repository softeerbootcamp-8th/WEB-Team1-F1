import { Link, useLocation } from 'react-router-dom'
import { Home, PackageSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { StatusBadge } from '@/components/common/status-badge'
import type { AuctionCreationResult } from '@/features/sell/types'
import { formatDateTime, formatKRW } from '@/lib/format'

export function SellResultPage() {
  const { state } = useLocation()
  const result = state as AuctionCreationResult | null

  if (!result) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="판매 신청 결과">
        <EmptyState
          icon={PackageSearch}
          title="판매 신청 정보가 없어요"
          description="내 차 팔기에서 번호판을 입력하면 경매가 등록돼요."
          action={
            <Button asChild>
              <Link to="/sell">내 차 팔기로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="판매 신청 결과">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Sell with RACE
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          경매가 등록됐어요!
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          아래 시각에 맞춰 경매방이 열려요. 시작 30분 전부터 입장할 수 있어요.
        </p>
      </header>

      <section className="bg-foreground text-background mt-12 rounded-2xl p-8">
        <div className="flex items-center justify-between">
          <p className="text-background/55 text-sm">경매 시작가</p>
          {/* 등록 직후는 시작이 최소 1시간 뒤라 방이 아직 열리지 않았다(result.status는 항상 SCHEDULED) */}
          <StatusBadge status="NOT_OPEN" />
        </div>
        <p className="tabular mt-3 text-4xl font-semibold">
          {formatKRW(result.startPrice)}
        </p>

        <dl className="mt-8 grid grid-cols-3 gap-4 text-sm">
          <div>
            <dt className="text-background/55">입장 가능</dt>
            <dd className="tabular mt-1 font-medium">
              {formatDateTime(result.roomOpenAt)}
            </dd>
          </div>
          <div>
            <dt className="text-background/55">경매 시작</dt>
            <dd className="tabular mt-1 font-medium">
              {formatDateTime(result.startAt)}
            </dd>
          </div>
          <div>
            <dt className="text-background/55">경매 마감</dt>
            <dd className="tabular mt-1 font-medium">
              {formatDateTime(result.endAt)}
            </dd>
          </div>
        </dl>
      </section>

      <div className="mt-10 flex gap-3">
        <Button asChild variant="outline" size="lg" className="flex-1">
          <Link to="/">
            <Home className="size-4" />
            홈으로 돌아가기
          </Link>
        </Button>
        <Button asChild size="lg" className="flex-1">
          <Link to={`/auctions/${result.auctionId}`}>경매 보러가기</Link>
        </Button>
      </div>
    </main>
  )
}
