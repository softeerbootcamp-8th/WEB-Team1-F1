import { useCallback, useEffect, useState } from 'react'

import { fetchAuctionList } from '@/features/auctions/api'
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
  /** false면 조회하지 않고 빈 상태로 둔다(예: 나의 경매인데 비로그인) */
  enabled?: boolean
}

/**
 * 경매 목록 조회 + 커서 기반 "더 보기" 페이지네이션.
 *
 * 범위(전체/나의 경매)나 상태 필터가 바뀌면 들고 있던 커서를 즉시 버리고 첫 페이지부터
 * 다시 읽는다. 커서는 목록에서의 위치가 아니라 정렬축(마감·시작 시각) 위의 좌표라,
 * 다른 목록에 그대로 쓰면 앞부분이 통째로 잘린 페이지를 받는다.
 */
export function useAuctionList({ scope, filter, enabled = true }: UseAuctionListOptions) {
  const [cards, setCards] = useState<AuctionListCard[]>([])
  const [cursor, setCursor] = useState<AuctionListCursor | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [isLoading, setIsLoading] = useState(enabled)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<unknown>(null)
  // 이어 읽기 실패는 첫 페이지 실패와 분리한다. 같이 두면 스크롤 도중 한 번 실패했을 때
  // 이미 보고 있던 목록이 통째로 에러 화면으로 바뀐다.
  const [loadMoreError, setLoadMoreError] = useState<unknown>(null)
  // 수정·삭제 후 다시 읽기 위한 트리거. 시작 시각이 바뀌면 속한 그룹과 정렬 위치까지 달라진다.
  const [reloadToken, setReloadToken] = useState(0)

  useEffect(() => {
    // 응답을 기다리는 동안 "더 보기"가 눌려도 이전 목록의 커서가 나가지 않도록 먼저 비운다.
    setCursor(null)
    setHasNext(false)

    setLoadMoreError(null)

    if (!enabled) {
      setCards([])
      setIsLoading(false)
      setError(null)
      return
    }

    let cancelled = false
    setIsLoading(true)

    fetchAuctionList({ scope, filter })
      .then((page) => {
        if (cancelled) return
        setCards(page.content)
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
        setError(null)
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
    }
  }, [scope, filter, enabled, reloadToken])

  const loadMore = useCallback(async () => {
    // 첫 페이지를 다시 읽는 중이면 지금 커서는 비어 있거나 이전 목록의 것이다.
    if (!cursor || isLoading || isLoadingMore) return

    setIsLoadingMore(true)
    setLoadMoreError(null)
    try {
      const page = await fetchAuctionList({ scope, filter, cursor })
      setCards((prev) => [...prev, ...page.content])
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)
    } catch (cause) {
      // 무한 스크롤은 실패해도 화면이 그대로라 자동으로 다시 시도하면 조용히 반복 호출된다.
      // 여기서 멈추고, 다시 시도할지는 사용자가 정하게 한다.
      setLoadMoreError(cause)
    } finally {
      setIsLoadingMore(false)
    }
  }, [cursor, isLoading, isLoadingMore, scope, filter])

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  return { cards, isLoading, isLoadingMore, hasNext, error, loadMoreError, loadMore, reload }
}
