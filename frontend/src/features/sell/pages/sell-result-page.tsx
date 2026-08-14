import { Link, useLocation } from 'react-router-dom'
import { FileText, Home, PackageSearch, SquareArrowOutUpRight } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { CarPhotos } from '@/features/auction-room/components/car-detail'
import { KeywordBadges } from '@/features/auction-room/components/keyword-badges'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL } from '@/features/quote/types'
import type { AuctionCreationResultState } from '@/features/sell/types'
import { formatClock, formatMileage } from '@/lib/format'

export function SellResultPage() {
  const { state } = useLocation()
  const result = state as AuctionCreationResultState | null

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
    <main className="mx-auto max-w-5xl px-6 py-10" aria-label="판매 신청 결과">
      <h1 className="mb-8 text-3xl font-semibold tracking-tight md:text-4xl">경매가 등록되었습니다</h1>
      <section className="overflow-hidden rounded-2xl border" aria-label="등록된 경매">
        <header className="flex flex-wrap items-start justify-between gap-4 border-b p-5 md:px-8">
          <div className="space-y-1">
            <p className="text-2xl font-semibold tracking-tight md:text-3xl">
              {MANUFACTURER_LABEL[result.vehicle.manufacturer]} {result.vehicle.model}
            </p>
            <p className="text-muted-foreground text-lg">
              {result.vehicle.modelYear}년 ·{' '}
              {result.vehicle.mileage === null ? '주행거리 정보 없음' : formatMileage(result.vehicle.mileage)} ·{' '}
              {FUEL_TYPE_LABEL[result.vehicle.fuelType]}
            </p>
            <KeywordBadges keywords={result.vehicle.keywords} />
          </div>
          <div className="flex shrink-0 self-center items-baseline gap-2 text-2xl font-bold">
            <span>시작가</span>
            <span className="tabular">{result.startPrice.toLocaleString('ko-KR')}원</span>
          </div>
        </header>

        <CarPhotos
          model={result.vehicle.model}
          imageUrls={result.vehicle.imageUrls}
          aspectClassName="aspect-[4/3] md:aspect-[5/2]"
          className="p-4 md:p-8"
        />

        <div className="grid gap-3 border-t px-5 py-4 sm:grid-cols-2 lg:grid-cols-4 md:px-8">
          <div className="text-muted-foreground flex min-w-0 items-center justify-self-center">
            <ScheduleFact label="경매방 입장" value={formatClock(result.roomOpenAt)} />
          </div>
          <div className="text-muted-foreground flex min-w-0 items-center justify-self-center">
            <ScheduleFact label="경매 시작" value={formatClock(result.startAt)} />
          </div>
          <div className="text-muted-foreground flex min-w-0 items-center justify-self-center">
            <ScheduleFact label="경매 마감" value={formatClock(result.endAt)} />
          </div>
          {result.vehicle.diagnosticReportUrl && (
            <Button asChild variant="outline" size="sm" className="justify-self-center">
              <a href={result.vehicle.diagnosticReportUrl} target="_blank" rel="noreferrer">
                <FileText /> 진단서 보기 <SquareArrowOutUpRight className="text-muted-foreground" />
              </a>
            </Button>
          )}
        </div>

      </section>

      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
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

function ScheduleFact({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline gap-1.5 whitespace-nowrap">
      <dt className="text-sm">{label}</dt>
      <dd className="text-foreground tabular font-semibold">{value}</dd>
    </div>
  )
}
