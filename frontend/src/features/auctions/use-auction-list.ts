import { useCallback, useEffect, useState } from 'react'

import { fetchAuctionList } from '@/features/auctions/api'
import type { AuctionListCard, AuctionListCursor } from '@/features/auctions/types'

/** 경매 목록 조회 + 커서 기반 "더 보기" 페이지네이션. */
export function useAuctionList() {
  const [cards, setCards] = useState<AuctionListCard[]>([])
  const [cursor, setCursor] = useState<AuctionListCursor | null>(null)
  const [hasNext, setHasNext] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isLoadingMore, setIsLoadingMore] = useState(false)

  useEffect(() => {
    let cancelled = false
    fetchAuctionList().then((page) => {
      if (cancelled) return
      setCards(page.content)
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)
      setIsLoading(false)
    })
    return () => {
      cancelled = true
    }
  }, [])

  const loadMore = useCallback(async () => {
    if (!cursor || isLoadingMore) return
    setIsLoadingMore(true)
    try {
      const page = await fetchAuctionList(cursor)
      setCards((prev) => [...prev, ...page.content])
      setCursor(page.nextCursor)
      setHasNext(page.hasNext)
    } finally {
      setIsLoadingMore(false)
    }
  }, [cursor, isLoadingMore])

  return { cards, isLoading, isLoadingMore, hasNext, loadMore }
}
