import { useCallback, useEffect, useRef, useState } from 'react'

import { fetchDealList } from './api'
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

  return { deals, hasNext, isLoading, isLoadingMore, error, loadMoreError, loadMore, reload }
}
