import { useCallback, useEffect, useRef, useState } from 'react'

import { incrementForPrice } from '@/lib/auction'
import { getErrorCode } from '@/lib/axios'
import {
  fetchAuctionRoom,
  fetchBidIncrementBands,
  fetchRoomOpening,
  fetchRoomResult,
  placeBid,
  subscribeRoomStream,
} from '@/features/auction-room/api'
import type {
  AuctionRoomView,
  BidIncrementBand,
  RoomEntry,
  RoomOpeningView,
  RoomResultView,
  RoomStreamState,
} from '@/features/auction-room/types'

const EXTENDED_FLAG_MS = 4000

// 개장 시각을 우리가 먼저 지나쳤다고 판단해도 서버는 아직 아닐 수 있다, 조금 늦게 두드린다
const REENTRY_BUFFER_MS = 500

// 서버가 고장 난 채로 남아 있으면 다시 들어가는 시도가 끝나지 않는다, 여기서 끊고 사용자에게 알린다
// 다섯 번이면 마지막 시도까지 15초쯤이라 정상적인 지연(1초 안쪽)은 다 흡수한다
const MAX_REENTRY_ATTEMPTS = 5

// 같은 방을 보던 사람들이 같은 박자로 몰려가지 않도록 대기 시간을 조금씩 흩는다
function backoffMs(attempt: number): number {
  return REENTRY_BUFFER_MS * 2 ** attempt * (0.8 + Math.random() * 0.4)
}
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
export function useAuctionRoom(auctionId: number) {
  const [room, setRoom] = useState<AuctionRoomView | null>(null)
  const [bands, setBands] = useState<BidIncrementBand[]>([])
  // 진입 결과, 들어갈 수 없으면 사유까지 들고 있어야 화면이 개장 안내나 결과로 옮겨갈 수 있다
  const [entry, setEntry] = useState<RoomEntry>('LOADING')
  const [opening, setOpening] = useState<RoomOpeningView | null>(null)
  const [result, setResult] = useState<RoomResultView | null>(null)
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
    let reentryTimer: number | null = null
    let resultAttempts = 0
    let reconnectAttempts = 0

    // 아직 열리지 않은 방은 안내를 받아 두고, 열리는 시각에 스스로 다시 들어간다
    const enterOpening = () => {
      fetchRoomOpening(auctionId)
        .then((view) => {
          if (cancelled) return

          const offsetMs = new Date(view.serverTime).getTime() - Date.now()
          setClockOffset(offsetMs)
          setOpening(view)
          setEntry('NOT_OPEN_YET')

          const delay = Math.max(0, new Date(view.openAt).getTime() - (Date.now() + offsetMs))
          reentryTimer = window.setTimeout(connect, delay + REENTRY_BUFFER_MS)
        })
        .catch((error: unknown) => {
          if (cancelled) return

          // 안내를 받는 사이 방이 열렸으면 이 API 가 409 로 막는다, 그때는 방으로 들어가면 된다
          if (getErrorCode(error) === 'ROOM_ALREADY_OPEN') {
            connect()
            return
          }

          setEntry('BROKEN')
        })
    }

    // 끝난 방은 더 이상 바뀌지 않는 요약을 받는다
    const enterResult = () => {
      fetchRoomResult(auctionId)
        .then((view) => {
          if (cancelled) return

          setResult(view)
          setEntry('CLOSED')
        })
        .catch((error: unknown) => {
          if (cancelled) return

          // 마감과 낙찰 확정 사이의 짧은 틈이다, 확정되면 결과가 나온다
          // 확정이 계속 실패한 채로 남으면 이 물음도 끝나지 않으므로 몇 번만 묻고 그만둔다
          if (getErrorCode(error) === 'AUCTION_NOT_ENDED') {
            if (resultAttempts >= MAX_REENTRY_ATTEMPTS) {
              setEntry('UNSTABLE')
              return
            }

            reentryTimer = window.setTimeout(enterResult, backoffMs(resultAttempts))
            resultAttempts += 1
            return
          }

          setEntry('BROKEN')
        })
    }

    // 연결이 끊기는 이유는 결과 구간이 끝나 서버가 끊는 것이 대부분이다, 오류로 덮지 않고 다시 들어가 본다
    // 정말 끝난 방이면 서버가 종료로 거절하고 화면은 결과 요약으로 이어진다
    //
    // 방 조회는 되는데 구독만 계속 거절당하는 상태도 있다. 그때 곧바로 다시 붙으면 지연이 없는 루프가
    // 되므로 시도할수록 간격을 늘리고 몇 번 뒤에는 그만둔다
    const reconnect = () => {
      unsubscribe?.()
      unsubscribe = null

      if (reconnectAttempts >= MAX_REENTRY_ATTEMPTS) {
        setEntry('UNSTABLE')
        return
      }

      reentryTimer = window.setTimeout(connect, backoffMs(reconnectAttempts))
      reconnectAttempts += 1
    }

    const connect = () => {
      fetchAuctionRoom(auctionId)
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
            unsubscribe = subscribeRoomStream(
              auctionId,
              (state) => {
                // 조회가 200 인 것으로는 부족하다, 구독이 값을 보내와야 연결이 산 것이다
                reconnectAttempts = 0
                mergeStreamState(state)
              },
              reconnect,
            )
          }
        })
        .catch((error: unknown) => {
          if (cancelled) return

          const reason = ENTRY_BY_ERROR_CODE[getErrorCode(error) ?? ''] ?? 'BROKEN'

          if (reason === 'NOT_OPEN_YET') {
            enterOpening()
            return
          }

          if (reason === 'CLOSED') {
            enterResult()
            return
          }

          setEntry(reason)
        })
    }

    connect()

    return () => {
      cancelled = true
      unsubscribe?.()
      if (reentryTimer !== null) window.clearTimeout(reentryTimer)
    }
  }, [auctionId, mergeStreamState])

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

  return {
    room,
    entry,
    opening,
    result,
    increment,
    nextMin,
    flashKey,
    extended,
    clockOffset,
    placeBid: bid,
  }
}
