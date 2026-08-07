import { Link } from 'react-router-dom'
import { DoorOpen, Gavel, Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useCountdown } from '@/hooks/use-countdown'
import { formatClock, formatDuration, formatKRW } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { RoomOpeningView } from '@/features/auction-room/types'

interface RoomNotOpenProps {
  opening: RoomOpeningView
  /** 서버 시각 - 브라우저 시계 */
  clockOffset: number
}

/**
 * 방이 열리기(시작 30분 전) 전 — 아직 방 밖이라 안내 한 장으로 끝낸다.
 * 방의 골격(시세판·호가창·입찰)은 여기 없다. 그것이 대기방과 이 화면을 가르는 것이다.
 */
export function RoomNotOpen({ opening, clockOffset }: RoomNotOpenProps) {
  const { remaining } = useCountdown(opening.openAt, 1000, clockOffset)

  return (
    <div className="dark bg-background text-foreground flex flex-col items-center rounded-xl border px-6 py-20 text-center">
      <div className="bg-muted mb-6 flex size-14 items-center justify-center rounded-full">
        <Lock className="size-6" />
      </div>
      <p className="text-muted-foreground text-sm font-medium tracking-widest uppercase">
        입장까지
      </p>
      <p className="tabular my-4 text-6xl font-bold tracking-tight md:text-7xl">
        {formatDuration(remaining)}
      </p>
      <p className="text-muted-foreground">아직 경매방이 열리지 않았어요</p>

      {/* 기다림이 두 번이라 순서를 미리 보여준다 — 입장한다고 곧바로 입찰이 아니라 대기방이다 */}
      <ol className="mt-8 flex flex-wrap items-center justify-center gap-4 text-sm">
        <Step icon={DoorOpen} label="입장 가능" at={opening.openAt} current />
        <li className="bg-border h-px w-8" aria-hidden />
        <Step icon={Gavel} label="입찰 시작" at={opening.startAt} />
      </ol>

      <p className="text-muted-foreground mt-6 text-sm">
        시작가{' '}
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

function Step({
  icon: Icon,
  label,
  at,
  current,
}: {
  icon: typeof DoorOpen
  label: string
  at: string
  current?: boolean
}) {
  return (
    <li
      className={cn(
        'flex items-center gap-2',
        current ? 'text-foreground' : 'text-muted-foreground',
      )}
      aria-current={current ? 'step' : undefined}
    >
      <Icon className="size-4" />
      <span className="font-medium">{label}</span>
      <span className="tabular">{formatClock(at)}</span>
    </li>
  )
}
