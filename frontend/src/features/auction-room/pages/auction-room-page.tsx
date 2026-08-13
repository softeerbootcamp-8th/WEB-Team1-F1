import { Link, Navigate, useLocation, useParams } from 'react-router-dom'
import { Eye, Gavel } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { HelpPopover } from '@/components/common/help-popover'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { useAuth } from '@/features/auth/auth-context'

import { useAuctionRoom } from '../use-auction-room'
import { PriceBoard } from '../components/price-board'
import { BidPanel } from '../components/bid-panel'
import { BidLedger } from '../components/bid-ledger'
import { BidIncrementHelp } from '../components/bid-increment-help'
import { WaitingRoom } from '../components/waiting-room'
import { CarDetail } from '../components/car-detail'
import { BackLink } from '../components/back-link'
import { KeywordBadges } from '../components/keyword-badges'
import { RoomStateBanner, RoomStateBar } from '../components/room-state-banner'
import type { RoomStateMode } from '../components/room-state-banner'
import type {
  AuctionRoomView,
  BidIncrementBand,
  RoomVehicle,
} from '../types'

export function AuctionRoomPage() {
  const { id } = useParams()
  const auctionId = Number(id)
  const { user, isAuthenticated, isLoading: authLoading } = useAuth()

  if (authLoading) return null

  // 서버가 경매방을 로그인한 사람에게만 열어 준다, 화면 단에서 먼저 로그인을 요구해 401 을 피한다
  if (!isAuthenticated || !user) {
    return <SignInNotice description="경매방은 로그인 후 볼 수 있어요." />
  }

  // 계정이 바뀌면 방을 새로 세운다. 내 입찰 표시 같은 상태가 이전 사람의 것으로 남으면 안 된다
  return <RoomContent key={user.id} auctionId={auctionId} />
}

/**
 * 차량 제목, 방이 열렸든 아니든 같은 자리에 온다.
 * 지금 단계는 제목 맞은편에 띠로 붙는다 — 같은 말을 배지와 띠가 두 번 하지 않게 배지는 두지 않는다.
 */
function RoomHeading({ vehicle, mode }: { vehicle: RoomVehicle; mode: RoomStateMode }) {
  return (
    <>
      <BackLink />

      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-stretch gap-3">
          <RoomStateBar mode={mode} />
          <div className="space-y-1">
            <span className="text-muted-foreground text-sm">{vehicle.modelYear}년</span>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
            </h1>
            <KeywordBadges keywords={vehicle.keywords} />
          </div>
        </div>

        <RoomStateBanner mode={mode} />
      </div>
    </>
  )
}

/** 로그인해야 볼 수 있다는 안내, 로그인 뒤 보던 경매방으로 돌아온다 */
function SignInNotice({ description }: { description: string }) {
  const location = useLocation()

  return (
    <main aria-label="경매방" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title="로그인이 필요합니다"
        description={description}
        action={
          <Button asChild>
            {/* 히스토리를 늘리지 않고 이 자리를 로그인 화면으로 바꾼다. 쌓아 두면 로그인을
                마친 뒤 방이 두 칸에 나란히 남아, 브라우저 뒤로가기가 켜져 있는데 눌러도
                같은 방이라 아무 일도 일어나지 않는다. 못 본 화면이라 되돌아갈 값도 없다 */}
            {/* state까지 되돌려 받는다. 목록이 실어 준 from이 로그인 왕복에서 증발하면
                로그인 뒤의 "뒤로"가 보던 탭이 아니라 목록 첫 화면으로 보낸다 */}
            <Link
              to="/login"
              replace
              state={{ returnTo: { pathname: location.pathname, state: location.state } }}
            >
              로그인
            </Link>
          </Button>
        }
      />
    </main>
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

function RoomContent({ auctionId }: { auctionId: number }) {
  const {
    room,
    entry,
    bands,
    increment,
    nextMin,
    flashKey,
    extended,
    clockOffset,
    placeBid,
  } = useAuctionRoom(auctionId)

  // 들어갈 수 없는 두 단계는 방이 아니라 목록 위 미리보기가 맡는다. 알림이나 북마크로 이 주소를
  // 바로 열었거나, 목록을 오래 열어 둔 사이 방이 닫힌 경우가 여기로 온다.
  // 뒤로가기가 닫힌 방으로 되돌아오지 않도록 히스토리를 남기지 않고 바꾼다.
  if (entry === 'NOT_OPEN_YET') {
    return <Navigate to={`/auctions?open=${auctionId}`} replace />
  }

  // 마감된 경매는 방이 아니라 결과 화면의 일이다. 방 안에서 결과를 그리면 5분이 지나면 사라지고,
  // 목록 미리보기로 보내면 끝난 뒤에도 남는 결과를 지나쳐 버린다
  if (entry === 'CLOSED') {
    return <Navigate to={`/auctions/${auctionId}/result`} replace />
  }

  if (entry === 'SIGNED_OUT') {
    return <SignInNotice description="로그인이 풀렸어요. 다시 로그인하면 이 경매방으로 돌아옵니다." />
  }

  if (entry === 'UNSTABLE') {
    return (
      <RoomNotice
        title="연결이 계속 끊기고 있어요"
        description="잠시 뒤 새로고침하면 다시 이어집니다."
      />
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

      {room.phase === 'WAITING' && <WaitingRoom room={room} clockOffset={clockOffset} />}

      {room.phase === 'LIVE' && (
        <LiveRoom
          room={room}
          bands={bands}
          increment={increment}
          nextMin={nextMin}
          flashKey={flashKey}
          extended={extended}
          clockOffset={clockOffset}
          placeBid={placeBid}
        />
      )}
    </main>
  )
}

/** 진행중 룸 레이아웃 (좌: 시세판/차량, 우: 입찰/호가창) */
function LiveRoom({
  room,
  bands,
  increment,
  nextMin,
  flashKey,
  extended,
  clockOffset,
  placeBid,
}: {
  room: AuctionRoomView
  /** 도움말 표가 쓰는 구간표 원본. 패널이 쓰는 increment 와 같은 출처다 */
  bands: BidIncrementBand[]
  // 구간표를 받기 전에는 상승가를 정할 수 없다, 그대로 입찰 패널까지 넘긴다
  increment: number | null
  nextMin: number | null
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

      {/* 차량과 호가창을 갈라 세 칸으로 둔다, 둘이 한 칸을 다투면 입찰 패널이 첫 화면 밖으로 밀린다 */}
      <div className="grid gap-5 lg:grid-cols-[1.05fr_1fr_360px]">
        <CarDetail vehicle={room.vehicle} />

        <div className="rounded-xl border p-5">
          <BidLedger bids={room.recentBids} totalBidCount={room.bidCount} />
        </div>

        <div className="space-y-4">
          <dl className="grid grid-cols-2 gap-3">
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-sm">
                <Eye className="size-4" />
                실시간 시청자
              </dt>
              <dd className="tabular mt-2 text-3xl font-semibold">
                {room.connectedCount}명
              </dd>
            </div>
            <div className="rounded-xl border p-4">
              <dt className="text-muted-foreground flex items-center gap-2 text-sm">
                <Gavel className="size-4" />
                입찰 참여자
              </dt>
              <dd className="tabular mt-2 text-3xl font-semibold">
                {room.bidderCount}명
              </dd>
            </div>
          </dl>

          {/* 도움말을 패널 밖에 둔다. 패널은 비로그인·평가사·판매자·마감·구간표 없음에서
              각각 다른 화면으로 갈리는데, 안에 넣으면 그 갈래마다 도움말이 사라진다 */}
          <div className="flex items-center justify-between pl-1">
            <h2 className="text-sm font-semibold">입찰</h2>
            <HelpPopover
              label="가격 구간별 최소 상승 금액 보기"
              contentClassName="w-80"
            >
              <BidIncrementHelp bands={bands} currentPrice={room.currentPrice} />
            </HelpPopover>
          </div>

          <BidPanel
            currentPrice={room.currentPrice}
            increment={increment}
            nextMin={nextMin}
            sellerIsMine={room.sellerIsMine}
            endAt={room.endAt}
            clockOffset={clockOffset}
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
