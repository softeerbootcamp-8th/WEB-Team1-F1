import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { SearchX, TriangleAlert } from 'lucide-react'

import { scrollToTop } from '@/app/scroll-reset'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { EmptyState } from '@/components/common/empty-state'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { AuctionPreviewDialog } from '@/features/auctions/components/auction-preview-dialog'
import type { PreviewStatus } from '@/features/auctions/components/auction-preview-dialog'
import { AuctionDeleteDialog } from '@/features/auctions/components/auction-delete-dialog'
import { AuctionEditDialog } from '@/features/auctions/components/auction-edit-dialog'
import { AuctionFilterPanel } from '@/features/auctions/components/auction-filter-panel'
import { MyAuctionActions } from '@/features/auctions/components/my-auction-actions'
import { ScopeTabs } from '@/features/auctions/components/scope-tabs'
import {
  EMPTY_FILTER,
  hasActiveFilter,
  readFilterParams,
  writeFilterParams,
  type AuctionVehicleFilter,
} from '@/features/auctions/filter'
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

  /**
   * 목록을 갈아끼우는 조건을 주소에 쓰고 화면을 맨 위로 올린다. 조건 패널이 sticky 라 목록
   * 한참 아래에서도 조건을 바꿀 수 있는데, 카드가 통째로 달라지므로 보던 높이는 새 목록에서
   * 아무 자리도 가리키지 않는다 — 새 목록이 더 짧으면 브라우저가 스크롤을 끝으로 당겨,
   * 무엇이 바뀌었는지 보이지 않는 자리에 남는다.
   *
   * replace 로 쓰는 것은 히스토리에 쌓지 않기 위해서다. 그래서 {@link useScrollReset} 이
   * 손대지 않고(미리보기를 닫기만 할 때 튀지 않으려면 그게 맞다), 여기서 직접 올린다.
   */
  const applyListParams = (params: URLSearchParams) => {
    setSearchParams(params, { replace: true })
    scrollToTop()
  }

  // 탭 전환은 히스토리에 쌓지 않는다. 쌓으면 탭을 옮긴 횟수만큼 뒤로가기를 눌러야 목록을 벗어난다.
  const selectTab = (key: string, value: string, isDefault: boolean) => {
    const next = new URLSearchParams(searchParams)
    if (isDefault) next.delete(key)
    else next.set(key, value.toLowerCase())
    applyListParams(next)
  }

  // 차량 조건도 탭처럼 주소가 원본이다. 경매방을 다녀와도, 주소를 공유해도 같은 조건이 복원된다.
  const vehicleFilter = useMemo(() => readFilterParams(searchParams), [searchParams])

  const changeVehicleFilter = (next: typeof vehicleFilter) => {
    const params = new URLSearchParams(searchParams)
    writeFilterParams(next, params)
    applyListParams(params)
  }

  // 조건과 상태를 한 번에 지운다. 나눠서 두 번 쓰면 둘 다 지금 주소에서 출발하므로
  // 나중 것이 앞에서 지운 것을 되살린다.
  const resetFilters = () => {
    const params = new URLSearchParams(searchParams)
    writeFilterParams(EMPTY_FILTER, params)
    params.delete(STATUS_PARAM)
    applyListParams(params)
  }

  const [editing, setEditing] = useState<AuctionListCard | null>(null)
  const [deleting, setDeleting] = useState<AuctionListCard | null>(null)

  // 입장할 수 없는 카드는 방으로 보내지 않고 여기서 연다. 단계는 열 때 판정한 값을 그대로 들고 있는다.
  const [preview, setPreview] = useState<{
    auctionId: number
    status: PreviewStatus
    card: AuctionListCard | null
  } | null>(null)

  /**
   * 알림이나 방에서 되돌아온 딥링크(`?open=3&as=closed`). 목록에 카드가 아직 없어도 열린다.
   * 방이 되돌릴 때 단계를 함께 실어 주므로 화면이 마감 + 5분을 다시 재지 않는다.
   */
  const deepLink = useMemo(() => {
    const id = Number(searchParams.get('open'))
    if (!Number.isInteger(id) || id <= 0) return null
    return { id, status: (searchParams.get('as') === 'closed' ? 'ENDED' : 'NOT_OPEN') as PreviewStatus }
  }, [searchParams])

  const closePreview = () => {
    setPreview(null)
    if (searchParams.has('open')) {
      const next = new URLSearchParams(searchParams)
      next.delete('open')
      next.delete('as')
      setSearchParams(next, { replace: true })
    }
  }

  /**
   * 미리보기에서 비슷한 조건으로 넘어올 때. 조건 쓰기와 미리보기 닫기를 한 번에 해야 한다,
   * 나눠 쓰면 resetFilters와 같은 이유로 나중 것이 앞의 것을 되살린다.
   * 상태 탭은 전체로 되돌린다. 마감된 경매를 보다 온 사람에게 종료 탭이 남아 있으면
   * 비슷한 경매도 끝난 것만 나온다.
   */
  const showSimilar = (filter: AuctionVehicleFilter) => {
    const params = new URLSearchParams(searchParams)
    writeFilterParams(filter, params)
    params.delete(STATUS_PARAM)
    params.delete('open')
    params.delete('as')
    applyListParams(params)
    setPreview(null)
  }

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
    vehicle: vehicleFilter,
    enabled: scope === 'ALL' || (!isAuthLoading && isAuthenticated),
    // 목록은 이어 보는 화면이다. 경매방의 "뒤로"는 뒤로가기가 아니라 이 주소로 새로 들어오는
    // 이동인데, 그때도 보던 자리로 돌아와야 한다
    restoreScroll: true,
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
    // 조건 패널과 목록이 나란히 서는 화면이라 다른 페이지보다 넓게 쓴다.
    <main aria-label="경매 목록" className="mx-auto max-w-[100rem] px-6 py-12">
      <header className="mb-8">
        <p className="text-muted-foreground text-sm">LIVE AUCTIONS</p>
        <h1 className="mt-2 text-3xl font-semibold md:text-4xl">경매 목록</h1>
        <p className="text-muted-foreground mt-2">
          평가가 완료된 차량의 실시간 가격 형성 과정을 확인하세요.
        </p>
      </header>

      {/* 조건은 목록 옆에 세워 둔다. 좁은 화면에서는 붙일 자리가 없어 목록 위로 접힌다. */}
      <div className="lg:grid lg:grid-cols-[23rem_1fr] lg:grid-rows-[auto_1fr] lg:items-start lg:gap-x-8">
        {/* 패널 윗변은 범위 탭이 아니라 카드 첫 줄에 맞춘다. 격자 둘째 줄에 놓으면 탭 높이를 재지 않아도 맞는다.
            상단 바(65px)가 sticky 라 그 아래에 세우고, 패널이 화면보다 길면 스스로 스크롤한다. */}
        <aside className="mb-6 lg:col-start-1 lg:row-start-2 lg:sticky lg:top-20 lg:mb-0 lg:max-h-[calc(100vh-6rem)] lg:overflow-y-auto">
          <AuctionFilterPanel
            value={vehicleFilter}
            onChange={changeVehicleFilter}
            status={filter === 'ALL' ? null : filter}
            onStatusChange={(next) =>
              selectTab(STATUS_PARAM, next ?? 'ALL', next === null)
            }
            onReset={resetFilters}
          />
        </aside>

        {/* 범위는 목록의 것이라 목록 열 머리에 둔다. 조건 패널과 나란히 서면 둘 다 필터로 읽힌다.
            내려가도 따라오게 고정한다 — 목록 한참 아래에서 범위를 바꾸려고 맨 위까지 되돌아가지
            않아도 된다. 상단 바 높이에 맞춰 그 바로 밑에 붙인다, 틈을 두면 그 사이로 카드가
            지나가 보인다. 탭 배경이 반투명이라 카드가 비치지 않게 불투명 배경을 함께 깐다. */}
        <div className="mb-6 lg:col-start-2 lg:row-start-1 lg:sticky lg:top-(--spacing-header) lg:z-10 lg:bg-background">
          <ScopeTabs
            value={scope}
            onChange={(next) => selectTab(SCOPE_PARAM, next, next === 'ALL')}
          />
        </div>

        <div className="lg:col-start-2 lg:row-start-2">
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
        // 조건 때문에 빈 것이면 상태 탭을 바꾸라는 안내가 틀린다. 되돌릴 길을 바로 준다.
        hasActiveFilter(vehicleFilter) ? (
          <EmptyState
            icon={SearchX}
            title="조건에 맞는 경매가 없습니다"
            description="조건을 줄이면 더 많은 차량을 볼 수 있어요."
            action={
              <Button variant="outline" onClick={resetFilters}>
                조건 초기화
              </Button>
            }
          />
        ) : (
          <EmptyState icon={SearchX} {...emptyMessage(scope, filter)} />
        )
      ) : (
        <>
          <ul className={GRID_CLASS}>
            {arranged.map((auction) => (
              <li key={auction.auctionId}>
                <AuctionCard
                  auction={auction}
                  nowMs={nowMs}
                  offsetMs={offsetMs}
                  onPreview={(card, status) =>
                    setPreview({ auctionId: card.auctionId, status, card })
                  }
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
        </div>
      </div>

      {/* 카드를 눌러 연 것이 우선이다, 없으면 딥링크가 연다 */}
      <AuctionPreviewDialog
        auctionId={preview?.auctionId ?? deepLink?.id ?? null}
        status={preview?.status ?? deepLink?.status ?? 'NOT_OPEN'}
        card={preview?.card ?? cards.find((c) => c.auctionId === deepLink?.id) ?? null}
        offsetMs={offsetMs}
        onOpenChange={(open) => !open && closePreview()}
        onSimilar={showSimilar}
      />
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
