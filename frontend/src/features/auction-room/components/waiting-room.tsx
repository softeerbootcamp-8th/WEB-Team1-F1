import { useCountdown } from '@/hooks/use-countdown'
import { formatDuration, formatClock, formatKRW } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { CalendarClock } from 'lucide-react'
import type { AuctionCard } from '@/types/domain'

interface WaitingRoomProps {
  auction: AuctionCard
  /** 시작 시각 도달 시 라이브로 자동 전환 */
  onStart: () => void
}

const ENTER_BEFORE_MS = 30 * 60_000 // 시작 30분 전 입장

/** 대기방 — 카운트다운 후 시작 시각 도달 시 라이브 자동 전환. */
export function WaitingRoom({ auction, onStart }: WaitingRoomProps) {
  const { remaining, isElapsed } = useCountdown(auction.startAt)

  if (isElapsed) onStart()

  const canEnter = remaining <= ENTER_BEFORE_MS

  return (
    <div className="dark bg-background text-foreground flex flex-col items-center rounded-xl border px-6 py-16 text-center">
      <div className="bg-muted mb-6 flex size-14 items-center justify-center rounded-full">
        <CalendarClock className="size-6" />
      </div>
      <p className="text-muted-foreground text-sm font-medium tracking-widest uppercase">
        경매 시작까지
      </p>
      <p className="tabular my-4 text-6xl font-bold tracking-tight md:text-7xl">
        {formatDuration(remaining)}
      </p>
      <p className="text-muted-foreground">
        {formatClock(auction.startAt)} 시작 예정 · 시작가{' '}
        <span className="text-foreground tabular font-semibold">
          {formatKRW(auction.startPrice)}
        </span>
      </p>

      <div className="mt-8">
        {canEnter ? (
          <p className="bg-price-up/15 text-price-up rounded-md px-4 py-2 text-sm font-medium">
            대기방에 입장했습니다. 시작 시각이 되면 자동으로 전환됩니다.
          </p>
        ) : (
          <Button variant="secondary" disabled>
            시작 30분 전부터 입장 가능
          </Button>
        )}
      </div>
    </div>
  )
}
