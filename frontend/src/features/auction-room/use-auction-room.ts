import { useCallback, useEffect, useRef, useState } from 'react'

import { incrementForPrice } from '@/lib/auction'
import { fetchAuctionRoom, fetchBidIncrementBands, placeBid } from '@/features/auction-room/api'
import type { AuctionRoomView, BidIncrementBand } from '@/features/auction-room/types'

const POLL_INTERVAL_MS = 2000
const EXTENDED_FLAG_MS = 4000

/**
 * 경매방 실시간 상태. 조회 자체가 접속 기록이 되므로 2초 주기로 폴링한다(백엔드 문서 지시).
 * WebSocket이 아니라 폴링 — 서버가 이렇게 설계했다.
 */
export function useAuctionRoom(auctionId: number, userId: number | null) {
  const [room, setRoom] = useState<AuctionRoomView | null>(null)
  const [bands, setBands] = useState<BidIncrementBand[]>([])
  const [error, setError] = useState(false)
  const [flashKey, setFlashKey] = useState(0)
  const [extended, setExtended] = useState(false)

  const prevPrice = useRef<number | null>(null)
  const prevEndAt = useRef<string | null>(null)

  useEffect(() => {
    fetchBidIncrementBands()
      .then(setBands)
      .catch(() => setBands([]))
  }, [])

  const poll = useCallback(async () => {
    if (userId == null) return
    try {
      const view = await fetchAuctionRoom(auctionId, userId)
      if (prevPrice.current !== null && view.currentPrice > prevPrice.current) {
        setFlashKey((k) => k + 1)
      }
      if (prevEndAt.current !== null && view.endAt !== prevEndAt.current) {
        setExtended(true)
        window.setTimeout(() => setExtended(false), EXTENDED_FLAG_MS)
      }
      prevPrice.current = view.currentPrice
      prevEndAt.current = view.endAt
      setRoom(view)
      setError(false)
    } catch {
      setError(true)
    }
  }, [auctionId, userId])

  useEffect(() => {
    if (userId == null) return
    poll()
    const id = window.setInterval(poll, POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [poll, userId])

  const increment = room ? incrementForPrice(room.currentPrice, bands) : 0
  // 첫 입찰은 시작가 그대로가 최소금액이다 — bidCount가 0이면 currentPrice가 곧 startPrice.
  const nextMin = room ? (room.bidCount === 0 ? room.currentPrice : room.currentPrice + increment) : 0

  const bid = useCallback(
    async (amount: number) => {
      await placeBid(auctionId, amount)
      await poll()
    },
    [auctionId, poll],
  )

  return { room, increment, nextMin, flashKey, extended, error, placeBid: bid }
}
