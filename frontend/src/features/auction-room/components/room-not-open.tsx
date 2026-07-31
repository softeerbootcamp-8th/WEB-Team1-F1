import { Link } from 'react-router-dom'
import { Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useCountdown } from '@/hooks/use-countdown'
import { formatClock, formatDuration } from '@/lib/format'
import type { AuctionRoomView } from '@/features/auction-room/types'

/** 방이 열리기(시작 30분 전) 전 — 차량 정보도 보여주지 않고 입장 자체를 막는다. */
export function RoomNotOpen({ room }: { room: AuctionRoomView }) {
  const { remaining } = useCountdown(room.openAt)

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
        {formatClock(room.openAt)}부터 입장할 수 있어요 (시작 30분 전)
      </p>

      <Button asChild variant="outline" className="mt-8">
        <Link to="/auctions">다른 경매 보기</Link>
      </Button>
    </div>
  )
}
