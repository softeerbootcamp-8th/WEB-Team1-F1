import { useEffect, useRef } from 'react'

interface UseInfiniteScrollOptions {
  /** false면 관찰을 멈춘다. 더 읽을 게 없거나 직전 요청이 실패했을 때 끈다. */
  enabled: boolean
  onLoadMore: () => void
  /**
   * 이 값이 바뀌면 관찰을 다시 건다. 목록 길이를 넘기면 된다.
   * IntersectionObserver는 교차 "상태가 바뀔 때"만 알리므로, 새 항목을 붙였는데도 감지 요소가
   * 여전히 rootMargin 안에 있으면 콜백이 오지 않아 그대로 멈춘다. 다시 관찰하면 현재 상태로
   * 한 번 더 판정한다.
   */
  observeKey: unknown
  /** 목록 끝에 닿기 전에 미리 불러올 거리 */
  rootMargin?: string
}

/**
 * 목록 끝의 감지용 요소가 보이면 다음 페이지를 부른다.
 * 반환한 ref를 목록 마지막에 둔 빈 요소에 걸면 된다.
 */
export function useInfiniteScroll({
  enabled,
  onLoadMore,
  observeKey,
  rootMargin = '400px',
}: UseInfiniteScrollOptions) {
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 콜백이 매 렌더 새로 만들어져도 관찰을 다시 걸지 않도록 최신 값만 들고 있는다.
  const onLoadMoreRef = useRef(onLoadMore)
  onLoadMoreRef.current = onLoadMore

  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!enabled || !sentinel) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) onLoadMoreRef.current()
      },
      { rootMargin },
    )
    observer.observe(sentinel)

    return () => observer.disconnect()
  }, [enabled, rootMargin, observeKey])

  return sentinelRef
}
