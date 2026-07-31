import { useCountdown } from '@/hooks/use-countdown'
import { formatDuration, formatClock, formatKRW } from '@/lib/format'
import { CalendarClock } from 'lucide-react'
import type { AuctionRoomView } from '@/features/auction-room/types'

interface WaitingRoomProps {
  room: AuctionRoomView
}

/**
 * 대기방 — 방이 열린(WAITING) 뒤부터만 렌더된다.
 * phase는 서버가 2초 폴링으로 알려주므로 여기서 자동 전환을 흉내내지 않는다 —
 * 다음 폴링에서 phase가 LIVE로 바뀌면 페이지가 알아서 다른 화면을 그린다.
 */
export function WaitingRoom({ room }: WaitingRoomProps) {
  const { remaining } = useCountdown(room.startAt)

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
        {formatClock(room.startAt)} 시작 예정 · 시작가{' '}
        <span className="text-foreground tabular font-semibold">
          {formatKRW(room.startPrice)}
        </span>
      </p>

      <div className="mt-8">
        <p className="bg-price-up/15 text-price-up rounded-md px-4 py-2 text-sm font-medium">
          대기방에 입장했습니다. 시작 시각이 되면 자동으로 전환됩니다.
        </p>
      </div>
    </div>
  )
}
