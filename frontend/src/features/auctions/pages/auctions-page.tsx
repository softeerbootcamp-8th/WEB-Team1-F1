import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { SearchX, TriangleAlert } from 'lucide-react'

import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { AuctionDeleteDialog } from '@/features/auctions/components/auction-delete-dialog'
import { AuctionEditDialog } from '@/features/auctions/components/auction-edit-dialog'
import { MyAuctionActions } from '@/features/auctions/components/my-auction-actions'
import { ScopeTabs } from '@/features/auctions/components/scope-tabs'
import { useAuctionList } from '@/features/auctions/use-auction-list'
import type { AuctionListCard, AuctionListScope } from '@/features/auctions/types'
import { useAuth } from '@/features/auth/auth-context'
import { useInfiniteScroll } from '@/hooks/use-infinite-scroll'
import { statusToListGroup } from '@/lib/auction'
import { getErrorMessage } from '@/lib/axios'
import type { AuctionStatus } from '@/types/domain'

type Filter = 'ALL' | AuctionStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'LIVE', label: '진행중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

const EMPTY_MESSAGE: Record<AuctionListScope, { title: string; description: string }> = {
  ALL: {
    title: '해당 상태의 경매가 없습니다',
    description: '다른 필터를 선택해 보세요.',
  },
  MINE: {
    title: '등록한 경매가 없습니다',
    description: '내 차 팔기에서 차량을 등록하면 여기에 표시됩니다.',
  },
}

export function AuctionsPage() {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()
  const [scope, setScope] = useState<AuctionListScope>('ALL')
  const [filter, setFilter] = useState<Filter>('ALL')
  const [editing, setEditing] = useState<AuctionListCard | null>(null)
  const [deleting, setDeleting] = useState<AuctionListCard | null>(null)

  // 상태 필터는 서버가 건다. 받아온 카드만 걸러내면 다음 페이지를 읽을수록 화면이 실제와 어긋난다.
  const listGroup = useMemo(
    () => (filter === 'ALL' ? null : statusToListGroup(filter)),
    [filter],
  )

  // 나의 경매는 세션이 있어야 조회된다. 세션 확인이 끝나기 전에 부르면 불필요한 401이 난다.
  // 확인이 끝날 때까지는 로그인 여부도 목록도 단정할 수 없어 로딩으로 둔다.
  const isSessionPending = scope === 'MINE' && isAuthLoading
  const needsLogin = scope === 'MINE' && !isAuthLoading && !isAuthenticated

  const {
    cards,
    isLoading,
    isLoadingMore,
    hasNext,
    error,
    loadMoreError,
    loadMore,
    reload,
  } = useAuctionList({
    scope,
    filter: listGroup,
    enabled: scope === 'ALL' || (!isAuthLoading && isAuthenticated),
  })

  // 실패한 뒤에는 관찰을 끊는다. 화면이 그대로라 계속 관찰하면 같은 요청을 무한히 반복한다.
  const sentinelRef = useInfiniteScroll({
    enabled: hasNext && !loadMoreError,
    onLoadMore: loadMore,
    observeKey: cards.length,
  })

  return (
    <main aria-label="경매 목록" className="mx-auto max-w-7xl px-6 py-12">
      <header className="mb-8">
        <div className="mb-6 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-muted-foreground text-sm">LIVE AUCTIONS</p>
            <h1 className="mt-2 text-3xl font-semibold md:text-4xl">경매 목록</h1>
            <p className="text-muted-foreground mt-2">
              평가가 완료된 차량의 실시간 가격 형성 과정을 확인하세요.
            </p>
          </div>
          <Tabs value={filter} onValueChange={(value) => setFilter(value as Filter)}>
            <TabsList>
              {FILTERS.map((item) => (
                <TabsTrigger key={item.value} value={item.value}>
                  {item.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>

        <ScopeTabs value={scope} onChange={setScope} />
      </header>

      {needsLogin ? (
        <EmptyState
          title="로그인이 필요합니다"
          description="내가 등록한 경매는 로그인 후 확인할 수 있어요."
          action={
            <Button asChild>
              <Link to="/login">로그인</Link>
            </Button>
          }
        />
      ) : error ? (
        <EmptyState
          icon={TriangleAlert}
          title="목록을 불러오지 못했습니다"
          description={getErrorMessage(error, '잠시 후 다시 시도해 주세요.')}
          action={
            <Button variant="outline" onClick={reload}>
              다시 시도
            </Button>
          }
        />
      ) : isLoading || isSessionPending ? (
        <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {Array.from({ length: 8 }, (_, index) => (
            <li key={index}>
              <Skeleton className="h-80 w-full rounded-xl" />
            </li>
          ))}
        </ul>
      ) : cards.length === 0 ? (
        <EmptyState icon={SearchX} {...EMPTY_MESSAGE[scope]} />
      ) : (
        <>
          <ul className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {cards.map((auction) => (
              <li key={auction.auctionId}>
                <AuctionCard
                  auction={auction}
                  actions={
                    scope === 'MINE' ? (
                      <MyAuctionActions
                        auction={auction}
                        onEdit={() => setEditing(auction)}
                        onDelete={() => setDeleting(auction)}
                      />
                    ) : undefined
                  }
                />
              </li>
            ))}
          </ul>

          {/* 목록 끝. 여기가 보이면 다음 페이지를 부른다. */}
          <div ref={sentinelRef} aria-hidden className="h-px" />

          <div aria-live="polite" className="mt-8 flex justify-center">
            {loadMoreError ? (
              <div className="text-center">
                <p className="text-muted-foreground mb-3 text-sm">
                  {getErrorMessage(loadMoreError, '다음 목록을 불러오지 못했습니다')}
                </p>
                <Button variant="outline" onClick={loadMore} disabled={isLoadingMore}>
                  다시 시도
                </Button>
              </div>
            ) : isLoadingMore ? (
              <p className="text-muted-foreground text-sm">불러오는 중…</p>
            ) : (
              !hasNext && (
                <p className="text-muted-foreground text-sm">마지막 경매까지 모두 봤어요</p>
              )
            )}
          </div>
        </>
      )}

      <AuctionEditDialog
        auction={editing}
        onOpenChange={(open) => !open && setEditing(null)}
        onUpdated={reload}
      />
      <AuctionDeleteDialog
        auction={deleting}
        onOpenChange={(open) => !open && setDeleting(null)}
        onDeleted={reload}
      />
    </main>
  )
}
