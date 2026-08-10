import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
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
import { useServerClock } from '@/hooks/use-server-clock'
import { arrangeCards, badgeStatusAt, statusToListGroup } from '@/lib/auction'
import { getErrorMessage } from '@/lib/axios'
import type { AuctionStatus } from '@/types/domain'

type Filter = 'ALL' | AuctionStatus

const FILTERS: { value: Filter; label: string }[] = [
  { value: 'ALL', label: '전체' },
  { value: 'LIVE', label: '진행중' },
  { value: 'SCHEDULED', label: '예정' },
  { value: 'ENDED', label: '종료' },
]

/**
 * 어느 탭을 보고 있었는지는 주소에 남긴다. 화면 상태로만 들고 있으면 경매방에 들어갔다
 * 돌아올 때 목록이 새로 마운트되면서 항상 첫 탭으로 풀린다.
 * 기본값(전체·모든 경매)은 아예 빼서 /auctions 주소를 깨끗하게 둔다.
 */
const STATUS_PARAM = 'status'
const SCOPE_PARAM = 'scope'

/**
 * 한 줄에 두 장. 네 장씩 놓으면 카드 하나에 담긴 사진·차종·가격이 모두 작아져
 * 무엇을 보고 고르는 화면인지가 흐려진다. 스켈레톤도 같은 격자를 써야 목록이
 * 들어올 때 자리가 그대로 유지된다.
 */
const GRID_CLASS = 'grid grid-cols-1 gap-5 md:grid-cols-2'

function readFilter(params: URLSearchParams): Filter {
  const raw = params.get(STATUS_PARAM)?.toUpperCase()
  return FILTERS.some((item) => item.value === raw) ? (raw as Filter) : 'ALL'
}

function readScope(params: URLSearchParams): AuctionListScope {
  return params.get(SCOPE_PARAM)?.toUpperCase() === 'MINE' ? 'MINE' : 'ALL'
}

/**
 * 빈 목록 문구. 범위만 보고 고르면 "나의 경매 + 진행중"에 결과가 없을 때
 * 경매를 여러 건 갖고 있는 사람에게도 "등록한 경매가 없습니다"라고 말하게 된다.
 */
function emptyMessage(scope: AuctionListScope, filter: Filter) {
  if (scope === 'MINE' && filter === 'ALL') {
    return {
      title: '등록한 경매가 없습니다',
      description: '내 차 팔기에서 차량을 등록하면 여기에 표시됩니다.',
    }
  }

  return {
    title: scope === 'MINE' ? '해당 상태의 내 경매가 없습니다' : '해당 상태의 경매가 없습니다',
    description: '다른 필터를 선택해 보세요.',
  }
}

export function AuctionsPage() {
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth()

  const [searchParams, setSearchParams] = useSearchParams()
  const scope = readScope(searchParams)
  const filter = readFilter(searchParams)

  // 탭 전환은 히스토리에 쌓지 않는다. 쌓으면 탭을 옮긴 횟수만큼 뒤로가기를 눌러야 목록을 벗어난다.
  const selectTab = (key: string, value: string, isDefault: boolean) => {
    const next = new URLSearchParams(searchParams)
    if (isDefault) next.delete(key)
    else next.set(key, value.toLowerCase())
    setSearchParams(next, { replace: true })
  }

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
    offsetMs,
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

  // 시계 하나로 화면 전체를 굴린다. 카드마다 두면 같은 순간에 카드끼리 다른 시각을 본다.
  const nowMs = useServerClock(offsetMs)

  // 서버가 준 순서를 지금 시각으로 다시 배치한다. 마감된 카드는 종료 무리로 내려가고,
  // 상태 탭이 켜져 있으면 그 그룹에서 벗어난 카드는 목록에서 빠진다.
  const arranged = useMemo(
    () => arrangeCards(cards, nowMs, listGroup),
    [cards, nowMs, listGroup],
  )

  // 실패한 뒤에는 관찰을 끊는다. 화면이 그대로라 계속 관찰하면 같은 요청을 무한히 반복한다.
  // 재배치가 아니라 불러온 개수로 관찰을 다시 건다. 자리만 바뀐 것은 다음 페이지와 무관하다.
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
          <Tabs
            value={filter}
            onValueChange={(value) => selectTab(STATUS_PARAM, value, value === 'ALL')}
          >
            <TabsList aria-label="경매 상태 필터">
              {FILTERS.map((item) => (
                <TabsTrigger key={item.value} value={item.value}>
                  {item.label}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
        </div>

        <ScopeTabs
          value={scope}
          onChange={(next) => selectTab(SCOPE_PARAM, next, next === 'ALL')}
        />
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
        <ul className={GRID_CLASS}>
          {Array.from({ length: 4 }, (_, index) => (
            // 카드 높이는 열 너비를 따라간다. 고정 높이로 두면 2열로 넓어진 카드와 어긋나
            // 목록이 들어올 때 화면이 튄다.
            <li key={index}>
              <Skeleton className="aspect-[4/3] w-full rounded-xl md:aspect-[5/4]" />
            </li>
          ))}
        </ul>
      ) : arranged.length === 0 ? (
        <EmptyState icon={SearchX} {...emptyMessage(scope, filter)} />
      ) : (
        <>
          <ul className={GRID_CLASS}>
            {arranged.map((auction) => (
              <li key={auction.auctionId}>
                <AuctionCard
                  auction={auction}
                  nowMs={nowMs}
                  offsetMs={offsetMs}
                  actions={
                    scope === 'MINE' ? (
                      <MyAuctionActions
                        status={badgeStatusAt(auction, nowMs)}
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
