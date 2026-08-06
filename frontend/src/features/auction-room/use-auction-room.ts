import { useCallback, useEffect, useRef, useState } from 'react'

import { incrementForPrice } from '@/lib/auction'
import {
  fetchAuctionRoom,
  fetchBidIncrementBands,
  placeBid,
  subscribeRoomStream,
} from '@/features/auction-room/api'
import type {
  AuctionRoomView,
  BidIncrementBand,
  RoomStreamState,
} from '@/features/auction-room/types'

const EXTENDED_FLAG_MS = 4000
const CONNECTABLE_PHASES = new Set(['WAITING', 'LIVE', 'RESULT'])

/**
 * 경매방 실시간 상태.
 * GET /room은 최초 진입 화면(내 입찰 여부 포함)만 그리고, 그 뒤 갱신은 /room/stream SSE
 * 구독으로 받는다(백엔드 문서 — 반복 조회가 아니라 구독). 스트림은 보는 사람을 가리지 않아
 * 내 입찰 표시가 없으므로, 최초 조회에서 알아낸 내 입찰 금액을 기억해뒀다가 직접 표시한다.
 */
export function useAuctionRoom(auctionId: number, userId: number | null) {
  const [room, setRoom] = useState<AuctionRoomView | null>(null)
  const [bands, setBands] = useState<BidIncrementBand[]>([])
  const [error, setError] = useState(false)
  const [flashKey, setFlashKey] = useState(0)
  const [extended, setExtended] = useState(false)
  // 서버 시각 - 이 브라우저 시계. 마감 시각은 서버가 정하는데 남은 시간을 브라우저 시계로 세면
  // 시계가 틀어진 사람은 다른 마감을 본다. 응답이 실어 주는 서버 시각으로 그 차이를 메운다
  const [clockOffset, setClockOffset] = useState(0)

  const myBidAmounts = useRef<Set<number>>(new Set())
  const prevPrice = useRef<number | null>(null)
  const prevEndAt = useRef<string | null>(null)

  useEffect(() => {
    fetchBidIncrementBands()
      .then(setBands)
      .catch(() => setBands([]))
  }, [])

  const mergeStreamState = useCallback((state: RoomStreamState) => {
    if (prevPrice.current !== null && state.currentPrice > prevPrice.current) {
      setFlashKey((k) => k + 1)
    }
    if (prevEndAt.current !== null && state.endAt !== prevEndAt.current) {
      setExtended(true)
      window.setTimeout(() => setExtended(false), EXTENDED_FLAG_MS)
    }
    prevPrice.current = state.currentPrice
    prevEndAt.current = state.endAt
    setClockOffset(new Date(state.serverTime).getTime() - Date.now())

    setRoom({
      auctionId: state.auctionId,
      phase: state.phase,
      vehicle: state.vehicle,
      startPrice: state.startPrice,
      currentPrice: state.currentPrice,
      openAt: state.openAt,
      startAt: state.startAt,
      endAt: state.endAt,
      serverTime: state.serverTime,
      connectedCount: state.connectedCount,
      bidderCount: state.bidderCount,
      bidCount: state.bidCount,
      winner:
        state.winner == null
          ? null
          : { name: state.winner.name, mine: myBidAmounts.current.has(state.currentPrice) },
      recentBids: state.recentBids.map((b) => ({
        ...b,
        mine: myBidAmounts.current.has(b.amount),
      })),
    })
  }, [])

  useEffect(() => {
    if (userId == null) return
    let cancelled = false
    let unsubscribe: (() => void) | null = null
    let retryTimer: number | null = null

    const connect = () => {
      fetchAuctionRoom(auctionId, userId)
        .then((view) => {
          if (cancelled) return

          view.recentBids.forEach((b) => {
            if (b.mine) myBidAmounts.current.add(b.amount)
          })
          if (view.winner?.mine) myBidAmounts.current.add(view.currentPrice)

          prevPrice.current = view.currentPrice
          prevEndAt.current = view.endAt
          setClockOffset(new Date(view.serverTime).getTime() - Date.now())
          setRoom(view)
          setError(false)

          if (CONNECTABLE_PHASES.has(view.phase)) {
            unsubscribe = subscribeRoomStream(auctionId, mergeStreamState, () => setError(true))
            return
          }

          // NOT_OPEN은 구독이 거절되므로(409), 방이 열리는 시각에 맞춰 다시 진입을 시도한다.
          if (view.phase === 'NOT_OPEN') {
            const delay = Math.max(0, new Date(view.openAt).getTime() - Date.now())
            retryTimer = window.setTimeout(connect, delay + 500)
          }
        })
        .catch(() => {
          if (!cancelled) setError(true)
        })
    }

    connect()

    return () => {
      cancelled = true
      unsubscribe?.()
      if (retryTimer !== null) window.clearTimeout(retryTimer)
    }
  }, [auctionId, userId, mergeStreamState])

  const increment = room ? incrementForPrice(room.currentPrice, bands) : 0
  // 첫 입찰은 시작가 그대로가 최소금액이다 — bidCount가 0이면 currentPrice가 곧 startPrice.
  const nextMin = room ? (room.bidCount === 0 ? room.currentPrice : room.currentPrice + increment) : 0

  const bid = useCallback(
    async (amount: number) => {
      const result = await placeBid(auctionId, amount)
      // 스트림이 곧 밀어주는 값에 mine을 붙일 수 있도록 미리 기억해둔다.
      myBidAmounts.current.add(amount)

      setClockOffset(new Date(result.serverTime).getTime() - Date.now())

      // 마감 직전 입찰은 마감을 뒤로 민다. 그 새 마감이 이 응답에 실려 오므로 스트림을 기다리지
      // 않고 바로 반영한다 — 기다리는 사이 화면은 이미 지난 마감을 향해 카운트다운한다.
      if (result.endAt !== prevEndAt.current) {
        prevEndAt.current = result.endAt
        setExtended(true)
        window.setTimeout(() => setExtended(false), EXTENDED_FLAG_MS)
        setRoom((prev) => (prev == null ? prev : { ...prev, endAt: result.endAt }))
      }
    },
    [auctionId],
  )

  return { room, increment, nextMin, flashKey, extended, error, clockOffset, placeBid: bid }
}
