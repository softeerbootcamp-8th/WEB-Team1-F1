import { useCallback, useEffect, useRef, useState } from 'react'

import { incrementForPrice } from '@/lib/auction'
import { getErrorCode } from '@/lib/axios'
import {
  fetchAuctionRoom,
  fetchBidIncrementBands,
  placeBid,
  subscribeRoomStream,
} from '@/features/auction-room/api'
import type {
  AuctionRoomView,
  BidIncrementBand,
  RoomEntry,
  RoomStreamState,
} from '@/features/auction-room/types'

const EXTENDED_FLAG_MS = 4000
const CONNECTABLE_PHASES = new Set(['WAITING', 'LIVE', 'RESULT'])

// 방에 들어갈 수 없는 사유는 서버가 코드로 알려준다, 화면이 시각을 보고 스스로 정하지 않는다
const ENTRY_BY_ERROR_CODE: Record<string, RoomEntry> = {
  ROOM_NOT_OPEN_YET: 'NOT_OPEN_YET',
  ROOM_ALREADY_CLOSED: 'CLOSED',
}

/**
 * 경매방 실시간 상태.
 * GET /room은 최초 진입 화면(내 입찰 여부 포함)만 그리고, 그 뒤 갱신은 /room/stream SSE
 * 구독으로 받는다(백엔드 문서 — 반복 조회가 아니라 구독). 스트림은 보는 사람을 가리지 않아
 * 내 입찰 표시가 없으므로, 최초 조회에서 알아낸 내 입찰 금액을 기억해뒀다가 직접 표시한다.
 */
export function useAuctionRoom(auctionId: number, userId: number | null) {
  const [room, setRoom] = useState<AuctionRoomView | null>(null)
  const [bands, setBands] = useState<BidIncrementBand[]>([])
  // 진입 결과, 들어갈 수 없으면 사유까지 들고 있어야 화면이 개장 안내나 결과로 옮겨갈 수 있다
  const [entry, setEntry] = useState<RoomEntry>('LOADING')
  const [flashKey, setFlashKey] = useState(0)
  const [extended, setExtended] = useState(false)
  // 서버 시각 - 이 브라우저 시계. 마감 시각은 서버가 정하는데 남은 시간을 브라우저 시계로 세면
  // 시계가 틀어진 사람은 다른 마감을 본다. 응답이 실어 주는 서버 시각으로 그 차이를 메운다
  const [clockOffset, setClockOffset] = useState(0)

  const myBidAmounts = useRef<Set<number>>(new Set())
  const prevPrice = useRef<number | null>(null)
  const prevEndAt = useRef<string | null>(null)
  const extendedTimer = useRef<number | null>(null)

  useEffect(() => {
    fetchBidIncrementBands()
      .then(setBands)
      .catch(() => setBands([]))
  }, [])

  // 연장이 잇달아 일어나면 먼저 건 타이머가 나중 연장의 표시를 조기에 끈다, 앞의 것을 취소하고 다시 건다
  const markExtended = useCallback(() => {
    setExtended(true)
    if (extendedTimer.current !== null) {
      window.clearTimeout(extendedTimer.current)
    }
    extendedTimer.current = window.setTimeout(() => setExtended(false), EXTENDED_FLAG_MS)
  }, [])

  useEffect(
    () => () => {
      if (extendedTimer.current !== null) {
        window.clearTimeout(extendedTimer.current)
      }
    },
    [],
  )

  const mergeStreamState = useCallback((state: RoomStreamState) => {
    if (prevPrice.current !== null && state.currentPrice > prevPrice.current) {
      setFlashKey((k) => k + 1)
    }
    if (prevEndAt.current !== null && state.endAt !== prevEndAt.current) {
      markExtended()
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
  }, [markExtended])

  useEffect(() => {
    if (userId == null) return

    // ref 는 컴포넌트 인스턴스에 붙어 있어 다른 방으로 옮겨도 살아남는다. 비우지 않으면 이전 방에서
    // 부른 금액이 새 방의 같은 금액을 내 것으로 만들어, 남이 낙찰받은 방에 내 이름이 뜬다
    myBidAmounts.current.clear()
    prevPrice.current = null
    prevEndAt.current = null
    // 이전 방의 차량과 가격이 새 응답이 올 때까지 그려지는 것도 같은 이유다
    setRoom(null)
    setExtended(false)

    let cancelled = false
    let unsubscribe: (() => void) | null = null

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
          setEntry('OPEN')

          // 열린 방만 응답하므로 여기 도달했으면 구독도 받아 준다
          if (CONNECTABLE_PHASES.has(view.phase)) {
            unsubscribe = subscribeRoomStream(auctionId, mergeStreamState, () => setEntry('BROKEN'))
          }
        })
        .catch((error: unknown) => {
          if (cancelled) return

          setEntry(ENTRY_BY_ERROR_CODE[getErrorCode(error) ?? ''] ?? 'BROKEN')
        })
    }

    connect()

    return () => {
      cancelled = true
      unsubscribe?.()
    }
  }, [auctionId, userId, mergeStreamState])

  const increment = room ? incrementForPrice(room.currentPrice, bands) : 0
  // 첫 입찰은 시작가 그대로가 최소금액이다 — bidCount가 0이면 currentPrice가 곧 startPrice.
  const nextMin = room ? (room.bidCount === 0 ? room.currentPrice : room.currentPrice + increment) : 0

  const bid = useCallback(
    async (amount: number) => {
      // 요청을 보내기 전에 적는다. 서버는 커밋 시점에 방송하고 그 방송이 이 응답보다 먼저 닿아,
      // 응답을 받고 적으면 내 호가가 남의 것으로 그려진 뒤다.
      // 실패하면 지운다 — 남겨두면 같은 금액을 부른 남의 호가에 내 표시가 붙는다.
      myBidAmounts.current.add(amount)

      let result
      try {
        result = await placeBid(auctionId, amount)
      } catch (error) {
        myBidAmounts.current.delete(amount)
        throw error
      }

      setClockOffset(new Date(result.serverTime).getTime() - Date.now())

      // 마감 직전 입찰은 마감을 뒤로 민다. 그 새 마감이 이 응답에 실려 오므로 스트림을 기다리지
      // 않고 바로 반영한다 — 기다리는 사이 화면은 이미 지난 마감을 향해 카운트다운한다.
      if (result.endAt !== prevEndAt.current) {
        prevEndAt.current = result.endAt
        markExtended()
        setRoom((prev) => (prev == null ? prev : { ...prev, endAt: result.endAt }))
      }
    },
    [auctionId, markExtended],
  )

  return { room, entry, increment, nextMin, flashKey, extended, clockOffset, placeBid: bid }
}
