import { Eye, Gavel, Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { useCountdown } from '@/hooks/use-countdown'
import { formatClock, formatDuration } from '@/lib/format'
import { BidLedger } from '@/features/auction-room/components/bid-ledger'
import { CarDetail } from '@/features/auction-room/components/car-detail'
import type { AuctionRoomView } from '@/features/auction-room/types'

interface WaitingRoomProps {
  room: AuctionRoomView
  /** 서버 시각 - 브라우저 시계 */
  clockOffset: number
}

/**
 * 대기방 — 방이 열린(WAITING) 뒤부터만 렌더된다.
 *
 * 진행중과 같은 골격을 쓴다(시세판 / 차량 / 시청자·호가창·입찰). 여기는 이미 방 안이고
 * 시작 시각에 이 자리가 그대로 열리기 때문이다. 값이 비어 있고 입찰이 잠겨 있을 뿐이라
 * 입장 전 안내와 한눈에 갈린다 — 그쪽은 방이 아니라 안내 한 장이다.
 *
 * phase는 구독이 밀어주므로 여기서 자동 전환을 흉내내지 않는다 —
 * 다음 전송에서 phase가 LIVE로 바뀌면 페이지가 알아서 다른 화면을 그린다.
 */
export function WaitingRoom({ room, clockOffset }: WaitingRoomProps) {
  const { remaining } = useCountdown(room.startAt, 1000, clockOffset)

  return (
    <div className="space-y-6">
      <StartBoard room={room} remaining={remaining} />

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <CarDetail vehicle={room.vehicle} />

        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-3">
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-xs">
                <Eye className="size-4" />
                함께 기다리는 사람
              </dt>
              <dd className="tabular mt-2 text-2xl font-semibold">{room.connectedCount}명</dd>
            </div>
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-xs">
                <Gavel className="size-4" />
                입찰 참여자
              </dt>
              <dd className="tabular mt-2 text-2xl font-semibold">{room.bidderCount}명</dd>
            </div>
          </dl>

          <div className="rounded-xl border p-5">
            <BidLedger
              bids={room.recentBids}
              totalBidCount={room.bidCount}
              emptyDescription="시작 시각이 되면 여기에 호가가 쌓입니다."
            />
          </div>

          <LockedBidPanel startAt={room.startAt} />
        </div>
      </div>
    </div>
  )
}

/**
 * 진행중의 시세판과 같은 자리, 같은 2단 구성.
 * 왼쪽에는 아직 오르지 않은 시작가가, 오른쪽에는 마감이 아니라 시작까지 남은 시간이 온다.
 *
 * 자리는 같게 두고 신호만 바꾼다 — 진행중은 실선 테두리에 초록 현재가, 여기는 점선 테두리에
 * 앰버 시작가다. 색만으로 가르면 색을 못 읽는 사람에게는 같은 화면이라 테두리도 함께 바꾼다.
 */
function StartBoard({ room, remaining }: { room: AuctionRoomView; remaining: number }) {
  return (
    <div className="dark bg-background text-foreground overflow-hidden rounded-xl border border-dashed">
      <div className="grid md:grid-cols-[1fr_auto]">
        <div className="border-border border-b border-dashed p-6 md:border-r md:border-b-0">
          <span className="text-muted-foreground text-xs font-medium tracking-widest uppercase">
            시작가
          </span>
          <div className="mt-3 flex items-baseline gap-2">
            <span className="tabular text-warning text-5xl font-bold tracking-tight tabular-nums md:text-6xl">
              {room.startPrice.toLocaleString('ko-KR')}
            </span>
            <span className="text-muted-foreground text-xl">원</span>
          </div>
          <p className="text-muted-foreground mt-3 text-sm">
            입찰이 시작되면 이 자리에 현재가가 오릅니다
          </p>
        </div>

        <div className="flex min-w-52 flex-col justify-center p-6 md:items-end md:text-right">
          <span className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium tracking-widest uppercase">
            <Lock className="size-3.5" />
            입찰 시작까지
          </span>
          <span className="tabular text-warning mt-2 text-3xl font-semibold">
            {formatDuration(remaining)}
          </span>
          <p className="text-muted-foreground mt-2 text-xs">
            {formatClock(room.startAt)} 시작 · {formatClock(room.endAt)} 마감 예정
          </p>
          <p className="text-muted-foreground mt-1 min-h-5 text-xs">이 화면에서 그대로 열립니다</p>
        </div>
      </div>
    </div>
  )
}

/** 입찰 패널이 앉을 자리를 미리 잡아 둔다, 시작 시각에 이 자리가 그대로 열린다 */
function LockedBidPanel({ startAt }: { startAt: string }) {
  return (
    <div className="rounded-xl border p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm font-semibold">입찰하기</span>
        <span className="text-muted-foreground text-xs">시작 전</span>
      </div>

      <div className="text-muted-foreground flex flex-col items-center gap-2 rounded-md border border-dashed py-6 text-center text-sm">
        <Lock className="size-4" />
        {formatClock(startAt)}에 입찰이 열립니다
      </div>

      <Button type="button" size="lg" className="mt-4 w-full" disabled>
        입찰 대기중
      </Button>
    </div>
  )
}
