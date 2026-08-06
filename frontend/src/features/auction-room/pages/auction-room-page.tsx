import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Eye, Gavel, Trophy } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { StatusBadge } from '@/components/common/status-badge'
import { formatClock, formatKRW } from '@/lib/format'
import { roomPhaseToStatus } from '@/lib/auction'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { useAuth } from '@/features/auth/auth-context'

import { useAuctionRoom } from '../use-auction-room'
import { PriceBoard } from '../components/price-board'
import { BidPanel } from '../components/bid-panel'
import { BidLedger } from '../components/bid-ledger'
import { WaitingRoom } from '../components/waiting-room'
import { RoomNotOpen } from '../components/room-not-open'
import { CarDetail } from '../components/car-detail'
import type { AuctionRoomView } from '../types'

export function AuctionRoomPage() {
  const { id } = useParams()
  const auctionId = Number(id)
  const { user, isAuthenticated, isLoading: authLoading } = useAuth()

  if (authLoading) return null

  // 경매방 조회는 아직 세션이 아니라 X-User-Id 임시 헤더로 "누구의 시점인지"를 받는다 —
  // 익명 조회 자체가 지원되지 않아, 화면 단에서 로그인부터 요구한다.
  if (!isAuthenticated || !user) {
    return (
      <main aria-label="경매방" className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="로그인이 필요합니다"
          description="경매방은 로그인 후 볼 수 있어요."
          action={
            <Button asChild>
              <Link to="/login">로그인</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return <RoomContent auctionId={auctionId} userId={user.id} />
}

function RoomContent({ auctionId, userId }: { auctionId: number; userId: number }) {
  const { room, increment, nextMin, flashKey, extended, error, clockOffset, placeBid } =
    useAuctionRoom(auctionId, userId)

  if (error) {
    return (
      <main aria-label="경매방" className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="경매를 찾을 수 없습니다"
          description="삭제되었거나 잘못된 주소입니다."
          action={
            <Button asChild variant="outline">
              <Link to="/">홈으로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  if (!room) {
    return <main aria-label="경매방" className="mx-auto max-w-7xl px-6 py-8" />
  }

  const status = roomPhaseToStatus(room.phase)

  return (
    <main aria-label={`${room.vehicle.model} 경매`} className="mx-auto max-w-7xl px-6 py-8">
      <Button variant="ghost" size="sm" asChild className="mb-4 -ml-2">
        <Link to="/">
          <ArrowLeft className="size-4" />
          목록으로
        </Link>
      </Button>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <div className="flex items-center gap-3">
            <StatusBadge status={status} />
            <span className="text-muted-foreground text-sm">{room.vehicle.modelYear}년</span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
            {MANUFACTURER_LABEL[room.vehicle.manufacturer]} {room.vehicle.model}
          </h1>
        </div>
      </div>

      {room.phase === 'NOT_OPEN' && <RoomNotOpen room={room} />}

      {room.phase === 'WAITING' && (
        <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
          <WaitingRoom room={room} clockOffset={clockOffset} />
          <CarDetail vehicle={room.vehicle} />
        </div>
      )}

      {room.phase === 'LIVE' && (
        <LiveRoom
          room={room}
          increment={increment}
          nextMin={nextMin}
          flashKey={flashKey}
          extended={extended}
          clockOffset={clockOffset}
          placeBid={placeBid}
        />
      )}

      {(room.phase === 'RESULT' || room.phase === 'CLOSED') && <EndedResult room={room} />}
    </main>
  )
}

/** 진행중 룸 레이아웃 (좌: 시세판/차량, 우: 입찰/호가창) */
function LiveRoom({
  room,
  increment,
  nextMin,
  flashKey,
  extended,
  clockOffset,
  placeBid,
}: {
  room: AuctionRoomView
  increment: number
  nextMin: number
  flashKey: number
  extended: boolean
  clockOffset: number
  placeBid: (amount: number) => Promise<void>
}) {
  return (
    <div className="space-y-6">
      <PriceBoard
        currentPrice={room.currentPrice}
        startPrice={room.startPrice}
        startAt={room.startAt}
        endAt={room.endAt}
        extended={extended}
        clockOffset={clockOffset}
        flashKey={flashKey}
      />

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <CarDetail vehicle={room.vehicle} />

        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-3">
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-xs">
                <Eye className="size-4" />
                실시간 시청자
              </dt>
              <dd className="tabular mt-2 text-2xl font-semibold">
                {room.connectedCount}명
              </dd>
            </div>
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-xs">
                <Gavel className="size-4" />
                입찰 참여자
              </dt>
              <dd className="tabular mt-2 text-2xl font-semibold">
                {room.bidderCount}명
              </dd>
            </div>
          </dl>

          <div className="rounded-xl border p-5">
            <BidLedger bids={room.recentBids} totalBidCount={room.bidCount} />
          </div>

          <BidPanel
            currentPrice={room.currentPrice}
            increment={increment}
            nextMin={nextMin}
            onBid={placeBid}
          />
        </div>
      </div>
    </div>
  )
}

/** 종료(결과 보기/마감) 화면 */
function EndedResult({ room }: { room: AuctionRoomView }) {
  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
      <CarDetail vehicle={room.vehicle} />
      <div className="flex flex-col gap-4">
        <div className="rounded-xl border p-6 text-center">
          {room.winner ? (
            <>
              <div className="bg-price-up/12 text-price-up mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
                <Trophy className="size-6" />
              </div>
              <p className="text-muted-foreground text-sm">최종 낙찰가</p>
              <p className="tabular text-price-up mt-1 text-3xl font-bold">
                {formatKRW(room.currentPrice)}
              </p>
              <p className="text-muted-foreground mt-2 text-sm">
                낙찰자 {room.winner.name} · 입찰 {room.bidCount}건
              </p>
              {room.winner.mine && (
                <Button asChild className="mt-5 w-full">
                  <Link to="/mypage">거래 진행하기</Link>
                </Button>
              )}
            </>
          ) : (
            <p className="text-muted-foreground">입찰 없이 유찰됐어요.</p>
          )}
        </div>

        <dl className="grid grid-cols-3 gap-px overflow-hidden rounded-xl border bg-border">
          <div className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">시작가</dt>
            <dd className="tabular mt-1 font-semibold">{formatKRW(room.startPrice)}</dd>
          </div>
          <div className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">입찰 참여자</dt>
            <dd className="tabular mt-1 font-semibold">{room.bidderCount}명</dd>
          </div>
          <div className="bg-card p-4">
            <dt className="text-muted-foreground text-xs">마감</dt>
            <dd className="tabular mt-1 font-semibold">{formatClock(room.endAt)}</dd>
          </div>
        </dl>
      </div>
    </div>
  )
}
