import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, Eye, Gavel, Trophy } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { StatusBadge } from '@/components/common/status-badge'
import { formatKRW } from '@/lib/format'
import { maskNickname } from '@/lib/format'
import { getAuctionById } from '@/features/auctions/mock'
import type { AuctionCard, AuctionStatus } from '@/types/domain'

import { useAuctionRoom } from '../use-auction-room'
import { PriceBoard } from '../components/price-board'
import { BidPanel } from '../components/bid-panel'
import { BidLedger } from '../components/bid-ledger'
import { WaitingRoom } from '../components/waiting-room'
import { CarDetail } from '../components/car-detail'
import { useAuth } from '@/features/auth/auth-context'

export function AuctionRoomPage() {
  const { id } = useParams()
  const auction = useMemo(() => getAuctionById(Number(id)), [id])

  // 상태 자동 전이(예정→진행→종료)를 로컬에서 시뮬레이션
  const [status, setStatus] = useState<AuctionStatus>(
    auction?.status ?? 'ENDED',
  )

  if (!auction) {
    return (
      <main aria-label="경매 상세" className="mx-auto max-w-3xl px-6 py-24">
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

  const effective = { ...auction, status }

  return (
    <main aria-label={`${auction.car.name} 경매`} className="mx-auto max-w-7xl px-6 py-8">
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
            <span className="text-muted-foreground text-sm">
              {auction.car.year}년
            </span>
          </div>
          <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
            {auction.car.name}
          </h1>
          <p className="text-muted-foreground text-sm">
            평가사 한줄평 · {auction.title}
          </p>
        </div>
      </div>

      {status === 'SCHEDULED' && (
        <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
          <WaitingRoom auction={auction} onStart={() => setStatus('LIVE')} />
          <CarDetail auction={auction} />
        </div>
      )}

      {status === 'LIVE' && (
        <LiveRoom auction={effective} onEnd={() => setStatus('ENDED')} />
      )}

      {status === 'ENDED' && <EndedResult auction={auction} />}
    </main>
  )
}

/** 진행중 룸 레이아웃 (좌: 시세판/차량, 우: 입찰/호가창) */
function LiveRoom({
  auction,
  onEnd,
}: {
  auction: AuctionCard
  onEnd: () => void
}) {
  const room = useAuctionRoom(auction)
  const { user } = useAuth()

  return (
    <div className="space-y-6">
      <PriceBoard
        currentPrice={room.currentPrice}
        startPrice={room.startPrice}
        endAt={room.endAt}
        extended={room.extended}
        flashKey={room.flashKey}
        onElapsed={onEnd}
      />

      <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
        <CarDetail auction={auction} />

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
          <BidLedger bids={room.bids} totalBidCount={room.totalBidCount} />
        </div>
          <BidPanel
            currentPrice={room.currentPrice}
            increment={room.increment}
            nextMin={room.nextMin}
            onBid={(amount) =>
              room.placeBid(amount, user?.nickname ?? '나', user?.role ?? 'USER')
            }
          />
        </div>
      </div>
    </div>
  )
}

/** 종료 결과 화면 */
function EndedResult({ auction }: { auction: AuctionCard }) {
  const { user } = useAuth()
  const iWon = user?.role === 'DEALER'

  return (
    <div className="grid gap-8 lg:grid-cols-[1.4fr_1fr]">
      <CarDetail auction={auction} />
      <div className="flex flex-col gap-4">
        <div className="rounded-xl border p-6 text-center">
          <div className="bg-price-up/12 text-price-up mx-auto mb-4 flex size-12 items-center justify-center rounded-full">
            <Trophy className="size-6" />
          </div>
          <p className="text-muted-foreground text-sm">최종 낙찰가</p>
          <p className="tabular text-price-up mt-1 text-3xl font-bold">
            {formatKRW(auction.currentPrice)}
          </p>
          <p className="text-muted-foreground mt-2 text-sm">
            낙찰자 {maskNickname('김민준')} · 입찰 {auction.bidCount}건
          </p>
          {iWon && (
            <Button asChild className="mt-5 w-full">
              <Link to="/mypage">거래 진행하기</Link>
            </Button>
          )}
        </div>
      </div>
    </div>
  )
}
