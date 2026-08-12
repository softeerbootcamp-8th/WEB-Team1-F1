import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router-dom'

import { keepScrollOnEnter } from '@/app/scroll-reset'
import { fetchAuctionList, subscribeAuctionListStream } from '@/features/auctions/api'
import type { AuctionVehicleFilter } from '@/features/auctions/filter'
import { EMPTY_FILTER, filterKey, hasActiveFilter } from '@/features/auctions/filter'
import { useDocumentVisible } from '@/hooks/use-document-visible'
import { applyAudienceEvent, applyCardEvent, serverClockOffset } from '@/lib/auction'
import type {
  AuctionListCard,
  AuctionListCursor,
  AuctionListGroup,
  AuctionListScope,
} from '@/features/auctions/types'

interface UseAuctionListOptions {
  scope: AuctionListScope
  /** null이면 상태 필터 없이 전체 */
  filter: AuctionListGroup | null
  /** 차량·가격 조건. 서버가 걸러 주므로 바뀌면 커서를 버리고 첫 페이지부터 다시 읽는다 */
  vehicle?: AuctionVehicleFilter
  /** false면 조회하지 않고 빈 상태로 둔다(예: 나의 경매인데 비로그인) */
  enabled?: boolean
  /**
   * 돌아왔을 때 보던 높이까지 되살릴지. 목록을 이어 보는 화면만 켠다.
   *
   * 목록 데이터 캐시와 나눠 둔 이유: 홈도 같은 캐시를 써서 경매방을 다녀와도 카드가 깜빡이지
   * 않아야 하지만, 홈은 이어 보는 화면이 아니다. 켜 두면 헤더의 "홈"을 눌렀을 때 지난번에
   * 보던 중간 높이로 떨어진다.
   */
  restoreScroll?: boolean
}

/** 화면을 떠났다 돌아올 때 이어 보기 위한 목록 한 벌 */
interface CachedList {
  cards: AuctionListCard[]
  cursor: AuctionListCursor | null
  hasNext: boolean
  offsetMs: number
  /** 목록을 떠나던 순간의 세로 스크롤. 돌아오면 이 높이에서 다시 시작한다. */
  scrollY: number
  /** 첫 페이지를 받은 시각. 이어 읽기로는 갱신하지 않는다 — 목록의 나이는 첫 페이지가 정한다. */
  fetchedAt: number
}

/**
 * 이 시간이 지난 캐시는 버리고 첫 페이지부터 다시 읽는다. 목록은 폴링이 없어 조회한
 * 순간부터 낡기 시작하는 화면이라, 경매방을 잠깐 다녀오는 왕복은 이어 보는 쪽이 낫고
 * 한참 만의 복귀는 현재가와 구성이 달라졌을 테니 새로 읽는 쪽이 맞다.
 */
const CACHE_TTL_MS = 2 * 60_000

/**
 * 경매방에 다녀와도 목록이 초기화되지 않도록 모듈에 남겨 두는 캐시.
 * 키는 범위와 필터의 조합 — 커서와 마찬가지로 다른 목록끼리 섞어 쓸 수 없다.
 */
const listCache = new Map<string, CachedList>()

function cacheKeyOf(scope: AuctionListScope, filter: AuctionListGroup | null, vehicleKey: string) {
  return `${scope}:${filter ?? 'ALL'}:${vehicleKey}`
}

function readFreshCache(key: string): CachedList | null {
  const entry = listCache.get(key)
  if (!entry) return null
  if (Date.now() - entry.fetchedAt > CACHE_TTL_MS) {
    listCache.delete(key)
    return null
  }
  return entry
}

/** 로그아웃 시 호출한다. "나의 경매" 캐시가 다음에 로그인한 사용자에게 보이면 안 된다. */
export function clearAuctionListCache() {
  listCache.clear()
}

/**
 * 경매 목록 조회 + 커서 기반 "더 보기" 페이지네이션.
 *
 * 범위(전체/나의 경매)나 상태 필터가 바뀌면 들고 있던 커서를 즉시 버리고 첫 페이지부터
 * 다시 읽는다. 커서는 목록에서의 위치가 아니라 정렬축(마감·시작 시각) 위의 좌표라,
 * 다른 목록에 그대로 쓰면 앞부분이 통째로 잘린 페이지를 받는다.
 *
 * 읽어 온 목록과 떠날 때의 스크롤은 모듈 캐시에 남긴다. 경매방에 들어갔다 뒤로가기로
 * 돌아오면 이 훅이 새로 마운트되는데, 그때 캐시가 신선하면 조회 없이 보던 자리부터 잇는다.
 */
export function useAuctionList({
  scope,
  filter,
  vehicle = EMPTY_FILTER,
  enabled = true,
  restoreScroll = false,
}: UseAuctionListOptions) {
  const visible = useDocumentVisible()

  // 조건은 객체라 렌더마다 새것일 수 있다. 값으로 만든 키를 기준으로 삼아야 같은 조건이 같은 목록이 된다.
  const vehicleKey = filterKey(vehicle)

  const { pathname } = useLocation()

  // 마운트하는 순간에 동기로 복원한다. 이펙트에서 하면 스켈레톤이 한 프레임 그려진 뒤
  // 목록으로 갈아끼워져 화면이 튀고, 스크롤을 되돌리려 해도 그 사이엔 되돌아갈 높이가 없다.
  const [restored] = useState(() => {
    const entry = enabled ? readFreshCache(cacheKeyOf(scope, filter, vehicleKey)) : null
    // 되살릴 높이를 들고 있으면 이 진입에서는 맨 위로 올리지 않도록 알린다. 경매방의 "뒤로"는
    // 뒤로가기가 아니라 목록 주소로 새로 들어가는 이동이라, 알리지 않으면 첫 화면으로 튄다.
    // 이펙트가 아니라 이 자리에서 세우는 이유는 아래 복원 이펙트보다 먼저여야 하기 때문이다
    if (entry && restoreScroll) keepScrollOnEnter(pathname)
    return entry
  })

  const [cards, setCards] = useState<AuctionListCard[]>(restored?.cards ?? [])
  const [cursor, setCursor] = useState<AuctionListCursor | null>(restored?.cursor ?? null)
  const [hasNext, setHasNext] = useState(restored?.hasNext ?? false)
  const [isLoading, setIsLoading] = useState(enabled && !restored)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<unknown>(null)
  // 서버 시각 - 이 브라우저의 시계. 서버는 남은 시간이 아니라 절대 시각을 주므로,
  // 시계가 어긋난 사람이 아직 진행 중인 경매를 끝난 것으로 보지 않으려면 이 값이 필요하다.
  // 두 시계의 간격은 목록을 다녀오는 동안 변하지 않으므로 복원해도 그대로 맞는다.
  const [offsetMs, setOffsetMs] = useState(restored?.offsetMs ?? 0)
  // 이어 읽기 실패는 첫 페이지 실패와 분리한다. 같이 두면 스크롤 도중 한 번 실패했을 때
  // 이미 보고 있던 목록이 통째로 에러 화면으로 바뀐다.
  const [loadMoreError, setLoadMoreError] = useState<unknown>(null)
  // 수정·삭제 후 다시 읽기 위한 트리거. 시작 시각이 바뀌면 속한 그룹과 정렬 위치까지 달라진다.
  const [reloadToken, setReloadToken] = useState(0)
  // 지금 보고 있는 목록의 세대. 목록이 갈릴 때마다 올려서, 이전 목록으로 띄운 요청의
  // 응답이 뒤늦게 도착해도 알아볼 수 있게 한다.
  const generationRef = useRef(0)

  // 복원으로 시작한 마운트에서는 조회를 건너뛰기 위한 표식. 첫 실행에서 소진하는 방식은
  // 안 된다 — StrictMode가 이펙트를 mount→cleanup→mount로 재실행해서, 두 번째 실행이
  // 표식 없이 재조회를 타며 복원한 목록을 스켈레톤으로 되돌린다.
  const restoredKeyRef = useRef(restored ? cacheKeyOf(scope, filter, vehicleKey) : null)

  // 보던 높이는 스크롤할 때마다 담아 뒀다가 떠날 때 캐시에 새긴다. 마운트 해제 시점에
  // window.scrollY를 읽으면 이미 다음 화면이 그려진 뒤라, 그 화면이 목록보다 짧으면
  // 브라우저가 잘라낸 높이가 남는다.
  const keyRef = useRef(cacheKeyOf(scope, filter, vehicleKey))
  keyRef.current = cacheKeyOf(scope, filter, vehicleKey)
  const scrollYRef = useRef(restored?.scrollY ?? 0)
  useEffect(() => {
    const record = () => {
      scrollYRef.current = window.scrollY
    }
    window.addEventListener('scroll', record, { passive: true })
    return () => {
      window.removeEventListener('scroll', record)
      const entry = listCache.get(keyRef.current)
      if (entry) entry.scrollY = scrollYRef.current
    }
  }, [])

  // 복원한 목록은 보던 높이로 옮겨 놓고 그린다. 카드가 첫 렌더부터 있고 사진 칸도
  // 비율(aspect-ratio)로 서 있어, 이미지가 오기 전에도 되돌아갈 높이는 확보돼 있다.
  // html에 scroll-behavior: smooth가 걸려 있어 instant를 명시한다. 복원이 애니메이션이 되면
  // 맨 위에서 보던 자리까지 화면이 흘러내려, 되돌아온 게 아니라 이동하는 것처럼 보인다.
  // 브라우저 뒤로가기(popstate)는 브라우저의 자체 스크롤 복원이 함께 돌므로 이 코드가
  // 없어도 자리가 맞는다. 이 코드는 앞으로 가는 진입(경매방의 "뒤로" 버튼)을 위한 것이다.
  useLayoutEffect(() => {
    if (restored && restoreScroll) {
      window.scrollTo({ top: restored.scrollY, behavior: 'instant' })
    }
  }, [restored, restoreScroll])

  useEffect(() => {
    const key = cacheKeyOf(scope, filter, vehicleKey)

    // 복원한 그 키를 계속 보고 있는 동안만 조회를 건너뛴다. reload가 불리면(토큰 > 0)
    // 캐시를 지웠으니 새로 읽어야 하고, 키가 갈렸다면 화면은 이미 다른 목록이며,
    // enabled가 꺼졌다면(예: 로그아웃) 아래로 내려가 복원한 목록을 비워야 한다.
    if (enabled && reloadToken === 0 && restoredKeyRef.current === key) {
      // 복원이라 조회는 없지만, 목록을 벗어날 때의 정리는 조회했을 때와 같아야 한다.
      return () => {
        generationRef.current += 1
      }
    }
    // 다른 키로 넘어간 순간 표식을 지운다. 남겨 두면 원래 키로 돌아왔을 때 화면에는
    // 다른 목록이 있는데도 조회를 건너뛴다.
    restoredKeyRef.current = null

    // 응답을 기다리는 동안 "더 보기"가 눌려도 이전 목록의 커서가 나가지 않도록 먼저 비운다.
    setCursor(null)
    setHasNext(false)

    setLoadMoreError(null)
    setIsLoadingMore(false)

    if (!enabled) {
      setCards([])
      setIsLoading(false)
      setError(null)
      return
    }

    let cancelled = false
    setIsLoading(true)

    fetchAuctionList({ scope, filter, vehicle })
      .then((page) => {
        if (cancelled) return
        // 응답이 도착한 이 순간에 잡는다. 렌더 시점에 재면 조회 이후 흐른 시간만큼 어긋난다.
        const offset = serverClockOffset(page.serverTime, Date.now())
        setOffsetMs(offset)
        setCards(page.content)
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
        setError(null)
        // 새 첫 페이지는 새 목록이다. 남아 있던 스크롤은 이전 목록의 것이라 함께 버린다.
        listCache.set(key, {
          cards: page.content,
          cursor: page.nextCursor,
          hasNext: page.hasNext,
          offsetMs: offset,
          scrollY: 0,
          fetchedAt: Date.now(),
        })
      })
      .catch((cause) => {
        if (cancelled) return
        setCards([])
        setError(cause)
      })
      .finally(() => {
        if (!cancelled) setIsLoading(false)
      })

    return () => {
      cancelled = true
      // 진행 중이던 이어 읽기 응답을 이 시점부터 무효로 만든다.
      generationRef.current += 1
    }
    // vehicleKey 로 건다. 조건 객체는 렌더마다 새것일 수 있어 그대로 걸면 조회가 끝없이 반복된다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope, filter, vehicleKey, enabled, reloadToken])

  // 스트림으로 바뀐 값이 캐시에도 닿아야 한다, 안 그러면 경매방을 다녀오는 순간 값이 되돌아간다
  // 업데이터 안에서 쓰지 않는다, StrictMode 가 업데이터를 두 번 불러 부수효과를 넣을 자리가 아니다
  useEffect(() => {
    const entry = listCache.get(cacheKeyOf(scope, filter, vehicleKey))
    if (entry) entry.cards = cards
  }, [cards, scope, filter, vehicleKey])

  const loadMore = useCallback(async () => {
    // 첫 페이지를 다시 읽는 중이면 지금 커서는 비어 있거나 이전 목록의 것이다.
    if (!cursor || isLoading || isLoadingMore) return

    // 응답이 오기 전에 목록이 갈릴 수 있다. 무한 스크롤은 자동으로 발화해서,
    // 스크롤 직후 탭을 누르면 이전 목록의 요청이 뜬 채로 남는다.
    const generation = generationRef.current
    const isStale = () => generation !== generationRef.current

    setIsLoadingMore(true)
    setLoadMoreError(null)
    try {
      const page = await fetchAuctionList({ scope, filter, vehicle, cursor })
      // 이전 목록의 페이지다. 그대로 붙이면 카드가 섞이고 커서까지 그 목록 것으로 덮인다.
      if (isStale()) return
      // 스트림이 끼워 넣은 카드가 이 페이지에 다시 올 수 있다
      const seen = new Set(cards.map((it) => it.auctionId))
      const merged = [...cards, ...page.content.filter((it) => !seen.has(it.auctionId))]
      setCards(merged)
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)

      // 돌아왔을 때도 여기까지 읽은 만큼 이어지도록 캐시에도 붙인다.
      const key = cacheKeyOf(scope, filter, vehicleKey)
      const entry = listCache.get(key)
      if (entry) {
        listCache.set(key, {
          ...entry,
          cards: merged,
          cursor: page.nextCursor,
          hasNext: page.hasNext,
        })
      }
    } catch (cause) {
      // 무한 스크롤은 실패해도 화면이 그대로라 자동으로 다시 시도하면 조용히 반복 호출된다.
      // 여기서 멈추고, 다시 시도할지는 사용자가 정하게 한다.
      if (isStale()) return
      setLoadMoreError(cause)
    } finally {
      // 목록이 갈렸다면 이 플래그는 이펙트가 이미 내렸다. 새 목록의 상태를 덮지 않는다.
      if (!isStale()) setIsLoadingMore(false)
    }
  }, [cards, cursor, isLoading, isLoadingMore, scope, filter, vehicle, vehicleKey])

  const reload = useCallback(() => {
    // 수정·삭제 뒤의 다시 읽기. 한 경매는 전체/나의 경매와 여러 상태 목록에 겹쳐 있어,
    // 지금 키만 지우면 다른 목록의 캐시에 낡은 카드가 그대로 남는다.
    listCache.clear()
    setReloadToken((token) => token + 1)
  }, [])

  // 이벤트가 도착한 그 순간의 서버 시각으로 판정해야 한다. 의존성에 넣으면 보정값이 바뀔 때마다
  // 구독을 다시 연다
  const offsetRef = useRef(offsetMs)
  offsetRef.current = offsetMs

  // 조건이 걸려 있는지도 같은 이유로 ref 다. 조건이 바뀔 때마다 구독을 다시 열 이유가 없다.
  const isFilteredRef = useRef(false)
  isFilteredRef.current = hasActiveFilter(vehicle)

  // 가려진 사이에 온 것은 유실이다. 서버가 다시 보내지 않으므로 돌아올 때 목록을 다시 읽는다.
  // onReconnect 는 같은 EventSource 가 스스로 붙을 때만 돌아서 이 경로를 대신하지 못한다
  const wasHidden = useRef(false)
  useEffect(() => {
    if (!visible) {
      wasHidden.current = true
      return
    }

    // 첫 표시에서는 부르지 않는다, 그때는 방금 조회한 목록이 최신이다
    if (wasHidden.current) {
      wasHidden.current = false
      reload()
    }
  }, [visible, reload])

  // filter 는 의존성이 아니다. 필터는 화면이 arrangeCards 로 거르는 것이라 구독을 다시 열 이유가 없다
  useEffect(() => {
    // 보지 않는 화면이 서버 연결을 물고 있을 이유가 없다
    if (!enabled || !visible) return

    return subscribeAuctionListStream({
      onCard: (card) => {
        setCards((current) => {
          // 조건이 걸린 목록에는 새 카드를 넣지 않는다. 무엇이 조건에 맞는지는 서버만 알고,
          // 카드에는 연료·변속기가 없어 여기서 판정할 수 없다. 이미 있는 카드의 갱신은 그대로 받는다.
          if (isFilteredRef.current && !current.some((it) => it.auctionId === card.auctionId)) {
            return current
          }
          return applyCardEvent(current, card, scope, Date.now() + offsetRef.current)
        })
      },
      onAudience: ({ auctionId, connectedCount }) => {
        setCards((current) => applyAudienceEvent(current, auctionId, connectedCount))
      },
      // 끊긴 동안 온 것은 유실이고 서버가 다시 보내지 않는다, 복구는 재조회 몫이다
      onReconnect: reload,
    })
  }, [scope, enabled, visible, reload])

  return {
    cards,
    offsetMs,
    isLoading,
    isLoadingMore,
    hasNext,
    error,
    loadMoreError,
    loadMore,
    reload,
  }
}
