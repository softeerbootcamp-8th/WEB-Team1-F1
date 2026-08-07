import { useState } from 'react'
import { Link } from 'react-router-dom'
import { LoaderCircle, PackageSearch } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { MOCK_AUCTIONS } from '@/features/auctions/mock'
import type { AuctionListCard, RoomPhase } from '@/features/auctions/types'
import { ROLE_LABEL, useAuth } from '@/features/auth/auth-context'
import { MyRequestsPanel } from '@/features/evaluations/components/my-requests-panel'
import { DealCard } from '../components/deal-card'
import { MOCK_DEALS } from '../mock'
import type { AuctionCard as MockAuctionCard, Deal } from '@/types/domain'

// 마이페이지는 아직 실제 Deal/참여 경매 API가 없어 mock을 쓴다.
// AuctionCard 컴포넌트는 실제 목록 API 계약을 따르므로 여기서만 변환해 맞춘다.
const STATUS_TO_PHASE: Record<MockAuctionCard['status'], RoomPhase> = {
  SCHEDULED: 'WAITING',
  LIVE: 'LIVE',
  ENDED: 'CLOSED',
}

function toListCard(auction: MockAuctionCard): AuctionListCard {
  return {
    auctionId: auction.id,
    phase: STATUS_TO_PHASE[auction.status],
    thumbnailUrl: auction.thumbnailUrl || null,
    model: auction.car.name,
    modelYear: auction.car.year,
    mileage: auction.car.mileageKm,
    startPrice: auction.startPrice,
    currentPrice: auction.currentPrice,
    openAt: auction.startAt,
    startAt: auction.startAt,
    endAt: auction.endAt,
    connectedCount: auction.participantCount,
  }
}

export function MyPage() {
  const { user, isAuthenticated, isLoading } = useAuth()
  const [deals, setDeals] = useState<Deal[]>(MOCK_DEALS)

  if (isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center" aria-label="로그인 확인 중">
        <LoaderCircle className="size-7 animate-spin" />
      </main>
    )
  }

  if (!isAuthenticated || !user) {
    return (
      <main aria-label="마이페이지" className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          icon={PackageSearch}
          title="로그인이 필요합니다"
          description="마이페이지는 로그인 후 이용할 수 있어요."
          action={
            <Button asChild>
              <Link to="/login">로그인</Link>
            </Button>
          }
        />
      </main>
    )
  }

  // 상태 전이 시뮬레이션
  const advance = (dealId: number, label: string) => {
    setDeals((prev) =>
      prev.map((d) => {
        if (d.id !== dealId) return d
        if (label === '거래 철회') return { ...d, status: 'CANCELLED' }
        if (label === '거래 확정') return { ...d, status: 'CONFIRMED' }
        if (label === '탁송 정보 입력') return { ...d, status: 'IN_TRANSIT' }
        if (label === '배송 정보 입력') return { ...d, status: 'COMPLETED' }
        return d
      }),
    )
  }

  const participated = MOCK_AUCTIONS.filter((a) => a.status !== 'SCHEDULED').slice(0, 4)

  return (
    <main aria-label="마이페이지" className="mx-auto max-w-5xl px-6 py-10">
      <header className="mb-8 flex items-center gap-3">
        <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
          {user.realName}
        </h1>
        <Badge variant="outline">{ROLE_LABEL[user.role]} 회원</Badge>
      </header>

      <Tabs defaultValue="evaluations">
        <TabsList>
          <TabsTrigger value="evaluations">방문견적</TabsTrigger>
          <TabsTrigger value="deals">내 거래</TabsTrigger>
          <TabsTrigger value="auctions">참여 경매</TabsTrigger>
        </TabsList>

        <TabsContent value="evaluations" className="mt-6">
          <MyRequestsPanel />
        </TabsContent>

        <TabsContent value="deals" className="mt-6">
          {deals.length === 0 ? (
            <EmptyState icon={PackageSearch} title="진행중인 거래가 없습니다" />
          ) : (
            <div className="grid gap-5 md:grid-cols-2">
              {deals.map((deal) => (
                <DealCard key={deal.id} deal={deal} onAction={advance} />
              ))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="auctions" className="mt-6">
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {participated.map((a) => (
              <li key={a.id}>
                <AuctionCard auction={toListCard(a)} />
              </li>
            ))}
          </ul>
        </TabsContent>
      </Tabs>
    </main>
  )
}
