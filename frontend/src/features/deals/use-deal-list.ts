import { useCallback, useEffect, useRef, useState } from 'react'

import { fetchDealList } from './api'
import { subscribeDealChanged } from './deal-events'
import type { DealCard } from './types'

/**
 * 내 거래 목록 + 커서 기반 "더 보기".
 *
 * react-query 대신 feature 훅에 상태를 두는 것은 목록 조회가 이미 그렇게 통일돼 있기 때문이다
 * (use-auction-list). 거래 하나 때문에 목록의 상태 관리 방식을 새로 들이지 않는다.
 *
 * 커서는 거래 식별자다. 단계 변경 시각이 아니라 식별자로 끊는 이유는 서버 쪽과 같다 —
 * 시각은 거래가 진행되면 바뀌어서, 페이지 사이에 순서가 뒤집히면 같은 거래가 두 번 보이거나
 * 통째로 건너뛴다.
 */
export function useDealList(enabled = true) {
  const [deals, setDeals] = useState<DealCard[]>([])
  const [cursor, setCursor] = useState<number | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [isLoading, setIsLoading] = useState(enabled)
  const [isLoadingMore, setIsLoadingMore] = useState(false)
  const [error, setError] = useState<unknown>(null)
  // 이어 읽기 실패는 첫 페이지 실패와 나눈다. 같이 두면 "더 보기"가 한 번 실패했을 때
  // 이미 보고 있던 목록이 통째로 에러 화면으로 바뀐다
  const [loadMoreError, setLoadMoreError] = useState<unknown>(null)
  const [reloadToken, setReloadToken] = useState(0)

  // 응답이 늦게 도착해도 알아볼 수 있게 세대를 센다. 다시 읽기를 연달아 누르면 순서가 뒤집힌다
  const generationRef = useRef(0)

  useEffect(() => {
    setCursor(null)
    setHasNext(false)
    setLoadMoreError(null)
    // 이어 읽기가 날아가는 중이었다면 그 응답은 세대가 어긋나 버려진다. 그쪽 finally 도 같은
    // 이유로 표시를 못 내리므로 여기서 내린다 — 안 내리면 "더 보기"가 영영 잠긴다
    setIsLoadingMore(false)

    if (!enabled) {
      setDeals([])
      setIsLoading(false)
      setError(null)
      return
    }

    const generation = ++generationRef.current
    setIsLoading(true)

    fetchDealList()
      .then((page) => {
        if (generation !== generationRef.current) return
        setDeals(page.content)
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
        setError(null)
      })
      .catch((cause) => {
        if (generation !== generationRef.current) return
        setDeals([])
        setError(cause)
      })
      .finally(() => {
        if (generation === generationRef.current) setIsLoading(false)
      })
  }, [enabled, reloadToken])

  const loadMore = useCallback(() => {
    if (cursor == null || isLoadingMore) return

    const generation = generationRef.current
    setIsLoadingMore(true)
    setLoadMoreError(null)

    fetchDealList(cursor)
      .then((page) => {
        if (generation !== generationRef.current) return
        // 이어 붙인다. 통째로 바꾸면 지금까지 읽은 페이지가 사라진다
        setDeals((prev) => [...prev, ...page.content])
        setCursor(page.nextCursor)
        setHasNext(page.hasNext)
      })
      .catch((cause) => {
        if (generation === generationRef.current) setLoadMoreError(cause)
      })
      .finally(() => {
        if (generation === generationRef.current) setIsLoadingMore(false)
      })
  }, [cursor, isLoadingMore])

  /** 거래를 한 단계 옮기고 목록으로 돌아왔을 때 첫 페이지부터 다시 읽는다 */
  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  // 상대가 단계를 넘기면 알림이 먼저 도착한다. 그 신호로 첫 페이지부터 다시 읽어, 목록에 남아
  // 있던 지난 단계와 "내 차례" 표시를 그 자리에서 맞춘다.
  // 이어 읽어 둔 페이지는 버린다 — 단계가 바뀐 거래의 자리가 함께 움직여서, 뒷 페이지만 그대로
  // 두면 같은 거래가 두 번 보이거나 통째로 빠진다
  useEffect(() => {
    if (!enabled) return

    return subscribeDealChanged(reload)
  }, [enabled, reload])

  return { deals, hasNext, isLoading, isLoadingMore, error, loadMoreError, loadMore, reload }
}
