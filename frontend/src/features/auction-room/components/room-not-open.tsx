import { Link } from 'react-router-dom'
import { Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useCountdown } from '@/hooks/use-countdown'
import { formatClock, formatDuration, formatKRW } from '@/lib/format'
import type { RoomOpeningView } from '@/features/auction-room/types'

interface RoomNotOpenProps {
  opening: RoomOpeningView
  /** 서버 시각 - 브라우저 시계 */
  clockOffset: number
}

/** 방이 열리기(시작 30분 전) 전 — 입장 가능 시각까지 남은 시간과 무엇이 나오는지를 보여준다. */
export function RoomNotOpen({ opening, clockOffset }: RoomNotOpenProps) {
  const { remaining } = useCountdown(opening.openAt, 1000, clockOffset)

  return (
    <div className="dark bg-background text-foreground flex flex-col items-center rounded-xl border px-6 py-20 text-center">
      <div className="bg-muted mb-6 flex size-14 items-center justify-center rounded-full">
        <Lock className="size-6" />
      </div>
      <p className="text-muted-foreground text-sm font-medium tracking-widest uppercase">
        아직 입장할 수 없어요
      </p>
      <p className="tabular my-4 text-6xl font-bold tracking-tight md:text-7xl">
        {formatDuration(remaining)}
      </p>
      <p className="text-muted-foreground">
        {formatClock(opening.openAt)}부터 입장할 수 있어요 (시작 30분 전)
      </p>
      <p className="text-muted-foreground mt-2">
        {formatClock(opening.startAt)} 입찰 시작 · 시작가{' '}
        <span className="text-foreground tabular font-semibold">
          {formatKRW(opening.startPrice)}
        </span>
      </p>

      <Button asChild variant="outline" className="mt-8">
        <Link to="/auctions">다른 경매 보기</Link>
      </Button>
    </div>
  )
}
