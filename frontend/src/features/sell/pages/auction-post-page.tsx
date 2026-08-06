import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { ArrowLeft, Check, Gavel } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { SchedulePicker } from '@/features/sell/components/schedule-picker'
import type { VehicleOwnerValues } from '@/features/vehicle/components/vehicle-owner-form'

const AUCTION_START_HOURS = Array.from({ length: 15 }, (_, i) => 10 + i) // 10시~24시(자정)
const MIN_LEAD_TIME_MS = 60 * 60 * 1000

function formatStartAt(date: Date) {
  return date.toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function AuctionPostPage() {
  const { state } = useLocation()
  const vehicle = state as VehicleOwnerValues | null

  const [startAt, setStartAt] = useState<Date | null>(null)
  const [submitted, setSubmitted] = useState(false)

  if (!vehicle?.ownerName || !vehicle.plateNumber) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="차량 정보를 먼저 입력해 주세요"
          description="내 차 팔기에서 차량 정보를 입력하면 경매 등록을 진행할 수 있습니다."
          action={
            <Button asChild>
              <Link to="/sell">내 차 팔기로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  if (submitted && startAt) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="경매글 등록 완료">
        <EmptyState
          icon={Check}
          title="경매글이 등록됐어요"
          description={`${formatStartAt(startAt)}에 경매가 시작됩니다.`}
          action={
            <Button asChild>
              <Link to="/mypage">마이페이지에서 확인</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="경매글 등록">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/sell">
          <ArrowLeft className="size-4" />
          내 차 팔기로
        </Link>
      </Button>

      <header className="mt-6 max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Register Auction
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          경매 시작 시간을 정해주세요.
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          지금부터 1시간 뒤부터, 오전 10시에서 밤 12시 사이로 시작 시간을 선택할 수 있어요.
        </p>
      </header>

      <div className="mt-12 grid gap-8 lg:grid-cols-[1fr_0.8fr]">
        <div className="min-w-0 rounded-2xl border p-7">
          <SchedulePicker
            hours={AUCTION_START_HOURS}
            minDateTime={new Date(Date.now() + MIN_LEAD_TIME_MS)}
            onSelect={setStartAt}
          />
        </div>

        <section className="bg-foreground text-background flex min-h-72 flex-col justify-between rounded-2xl p-8">
          <div>
            <p className="text-background/55 text-sm">경매 차량</p>
            <p className="mt-3 text-2xl font-semibold">{vehicle.ownerName}</p>
            <p className="text-background/55 tabular mt-1 text-sm">
              {vehicle.plateNumber}
            </p>
            <p className="text-background/55 mt-6 text-sm">경매 시작 시각</p>
            <p className="tabular mt-1 text-lg font-semibold">
              {startAt ? formatStartAt(startAt) : '아직 선택하지 않았어요'}
            </p>
          </div>

          <Button
            size="lg"
            className="mt-8 w-full"
            variant="secondary"
            disabled={!startAt}
            onClick={() => setSubmitted(true)}
          >
            <Gavel className="size-4" />
            경매 등록하기
          </Button>
        </section>
      </div>
    </main>
  )
}
