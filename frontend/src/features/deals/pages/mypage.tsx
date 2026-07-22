import { useState } from 'react'
import { Link } from 'react-router-dom'
import { PackageSearch } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { MOCK_AUCTIONS } from '@/features/auctions/mock'
import { ROLE_LABEL, useAuth } from '@/features/auth/auth-context'
import { DealCard } from '../components/deal-card'
import { MOCK_DEALS } from '../mock'
import type { Deal } from '@/types/domain'

export function MyPage() {
  const { user, isAuthenticated } = useAuth()
  const [deals, setDeals] = useState<Deal[]>(MOCK_DEALS)

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
          {user.nickname}
        </h1>
        <Badge variant="outline">{ROLE_LABEL[user.role]} 회원</Badge>
      </header>

      <Tabs defaultValue="deals">
        <TabsList>
          <TabsTrigger value="deals">내 거래</TabsTrigger>
          <TabsTrigger value="auctions">참여 경매</TabsTrigger>
        </TabsList>

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
                <AuctionCard auction={a} />
              </li>
            ))}
          </ul>
        </TabsContent>
      </Tabs>
    </main>
  )
}
