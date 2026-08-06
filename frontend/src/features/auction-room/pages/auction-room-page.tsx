import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Eye, Gavel, Trophy } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { formatClock, formatKRW } from '@/lib/format'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { useAuth } from '@/features/auth/auth-context'

import { useAuctionRoom } from '../use-auction-room'
import { PriceBoard } from '../components/price-board'
import { BidPanel } from '../components/bid-panel'
import { BidLedger } from '../components/bid-ledger'
import { WaitingRoom } from '../components/waiting-room'
import { RoomNotOpen } from '../components/room-not-open'
import { CarDetail } from '../components/car-detail'
import { RoomStateBanner, RoomStateBar } from '../components/room-state-banner'
import type { RoomStateMode } from '../components/room-state-banner'
import type { AuctionRoomView, RoomResultView, RoomVehicle, RoomWinner } from '../types'

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

/**
 * 목록으로 돌아가는 길과 차량 제목, 방이 열렸든 아니든 같은 자리에 온다.
 * 지금 단계는 제목 맞은편에 띠로 붙는다 — 같은 말을 배지와 띠가 두 번 하지 않게 배지는 두지 않는다.
 */
function RoomHeading({ vehicle, mode }: { vehicle: RoomVehicle; mode: RoomStateMode }) {
  return (
    <>
      <Button variant="ghost" size="sm" asChild className="mb-4 -ml-2">
        <Link to="/">
          <ArrowLeft className="size-4" />
          목록으로
        </Link>
      </Button>

      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-stretch gap-3">
          <RoomStateBar mode={mode} />
          <div className="space-y-1">
            <span className="text-muted-foreground text-sm">{vehicle.modelYear}년</span>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
            </h1>
          </div>
        </div>

        <RoomStateBanner mode={mode} />
      </div>
    </>
  )
}

/** 방 대신 사유를 알리는 화면 */
function RoomNotice({ title, description }: { title: string; description: string }) {
  return (
    <main aria-label="경매방" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title={title}
        description={description}
        action={
          <Button asChild variant="outline">
            <Link to="/auctions">다른 경매 보기</Link>
          </Button>
        }
      />
    </main>
  )
}

function RoomContent({ auctionId, userId }: { auctionId: number; userId: number }) {
  const {
    room,
    entry,
    opening,
    result,
    increment,
    nextMin,
    flashKey,
    extended,
    clockOffset,
    placeBid,
  } = useAuctionRoom(auctionId, userId)

  if (entry === 'NOT_OPEN_YET' && opening) {
    return (
      <main
        aria-label={`${opening.vehicle.model} 경매`}
        className="mx-auto max-w-7xl px-6 py-8"
      >
        <RoomHeading vehicle={opening.vehicle} mode="NOT_OPEN" />

        <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
          <RoomNotOpen opening={opening} clockOffset={clockOffset} />
          <CarDetail vehicle={opening.vehicle} />
        </div>
      </main>
    )
  }

  if (entry === 'CLOSED' && result) {
    return (
      <main aria-label={`${result.vehicle.model} 경매`} className="mx-auto max-w-7xl px-6 py-8">
        <RoomHeading vehicle={result.vehicle} mode="CLOSED" />
        <EndedResult summary={endedFromResult(result)} />
      </main>
    )
  }

  if (entry === 'BROKEN') {
    return <RoomNotice title="경매를 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다." />
  }

  if (!room) {
    return <main aria-label="경매방" className="mx-auto max-w-7xl px-6 py-8" />
  }

  return (
    <main aria-label={`${room.vehicle.model} 경매`} className="mx-auto max-w-7xl px-6 py-8">
      <RoomHeading vehicle={room.vehicle} mode={room.phase} />

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

      {room.phase === 'RESULT' && <EndedResult summary={endedFromRoom(room)} />}
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

/**
 * 끝난 경매가 화면에 보여줄 것.
 * 마감 직후에는 아직 열려 있는 방의 현황에서, 방이 닫힌 뒤에는 결과 요약에서 온다.
 * 결과 요약에는 접속자 수와 서버 시각이 없어 뒤의 둘은 있을 때만 채운다.
 */
interface EndedSummary {
  vehicle: RoomVehicle
  startPrice: number
  /** 유찰이면 없다 */
  winningPrice: number | null
  winner: RoomWinner | null
  bidCount: number
  bidderCount?: number
  endAt?: string
}

function endedFromRoom(room: AuctionRoomView): EndedSummary {
  return {
    vehicle: room.vehicle,
    startPrice: room.startPrice,
    // 마지막으로 성립한 입찰이 곧 낙찰가다
    winningPrice: room.winner ? room.currentPrice : null,
    winner: room.winner,
    bidCount: room.bidCount,
    bidderCount: room.bidderCount,
    endAt: room.endAt,
  }
}

function endedFromResult(result: RoomResultView): EndedSummary {
  return {
    vehicle: result.vehicle,
    startPrice: result.startPrice,
    winningPrice: result.winningPrice,
    winner: result.winner,
    bidCount: result.bidCount,
  }
}

/** 종료(결과 보기/마감) 화면 */
function EndedResult({ summary }: { summary: EndedSummary }) {
  const { user } = useAuth()

  // 서버는 낙찰자 이름을 누구에게나 마스킹해서 내려준다. 본인에게만은 실명이 자연스러운데,
  // 그 이름은 서버에 다시 물을 것 없이 내 세션이 이미 들고 있다
  const winnerName = summary.winner?.mine && user ? user.realName : summary.winner?.name

  // 시작가와 입찰 건수는 어느 출처로 오든 있다, 뒤의 둘은 방이 아직 열려 있을 때만 온다
  const facts: { label: string; value: string }[] = [
    { label: '시작가', value: formatKRW(summary.startPrice) },
    { label: '입찰', value: `${summary.bidCount}건` },
    ...(summary.bidderCount === undefined
      ? []
      : [{ label: '입찰 참여자', value: `${summary.bidderCount}명` }]),
    ...(summary.endAt === undefined ? [] : [{ label: '마감', value: formatClock(summary.endAt) }]),
  ]

  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
      <CarDetail vehicle={summary.vehicle} />
      <div className="flex flex-col gap-4">
        <div className="rounded-xl border p-6 text-center">
          {summary.winner && summary.winningPrice !== null ? (
            <>
              <div className="bg-price-up/12 text-price-up mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
                <Trophy className="size-6" />
              </div>
              <p className="text-muted-foreground text-sm">최종 낙찰가</p>
              <p className="tabular text-price-up mt-1 text-3xl font-bold">
                {formatKRW(summary.winningPrice)}
              </p>
              <p className="text-muted-foreground mt-2 text-sm">
                낙찰자 {winnerName}
                {summary.winner.mine && <span className="text-foreground font-medium"> (나)</span>}{' '}
                · 입찰 {summary.bidCount}건
              </p>
              {summary.winner.mine && (
                <Button asChild className="mt-5 w-full">
                  <Link to="/mypage">거래 진행하기</Link>
                </Button>
              )}
            </>
          ) : (
            <p className="text-muted-foreground">입찰 없이 유찰됐어요.</p>
          )}
        </div>

        <dl className="grid grid-cols-2 gap-px overflow-hidden rounded-xl border bg-border">
          {facts.map((fact) => (
            <div key={fact.label} className="bg-card p-4">
              <dt className="text-muted-foreground text-xs">{fact.label}</dt>
              <dd className="tabular mt-1 font-semibold">{fact.value}</dd>
            </div>
          ))}
        </dl>
      </div>
    </div>
  )
}
