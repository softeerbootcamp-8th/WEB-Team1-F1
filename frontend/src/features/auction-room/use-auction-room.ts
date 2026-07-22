import { useCallback, useEffect, useRef, useState } from 'react'

import { bidIncrement, SOFT_CLOSE_THRESHOLD_MS } from '@/lib/auction'
import { maskNickname } from '@/lib/format'
import type { AuctionCard, Bid } from '@/types/domain'
import { mockBids } from '@/features/auctions/mock'

/**
 * 경매 룸 실시간 상태 (개발용 시뮬레이션).
 * 실제로는 WebSocket 스냅샷/증분으로 대체된다:
 *  - 최고가 원자적 갱신, 소프트 클로즈 연장, 재접속 스냅샷 재동기화
 * 여기서는 타이머로 타 유저 입찰을 흉내내고, 임계 30초 내 입찰 시 종료시각을 리셋한다.
 */
export function useAuctionRoom(auction: AuctionCard) {
  const [currentPrice, setCurrentPrice] = useState(auction.currentPrice)
  const [bids, setBids] = useState<Bid[]>(() =>
    mockBids(auction).map((b) => ({ ...b, bidderNickname: maskNickname(b.bidderNickname) })),
  )
  const [endAt, setEndAt] = useState(auction.endAt)
  const [extended, setExtended] = useState(false)
  const [flashKey, setFlashKey] = useState(0)
  const seq = useRef(auction.id * 100000)
  // 최신 현재가를 인터벌 콜백에서 참조하기 위한 ref (state 클로저 회피)
  const priceRef = useRef(currentPrice)

  const increment = bidIncrement(currentPrice)
  const nextMin = currentPrice + increment

  /** 소프트 클로즈: 임계 이내 입찰이면 종료시각을 (입찰시각 + 30초)로 연장 */
  const applySoftClose = useCallback(() => {
    const remaining = new Date(endAt).getTime() - Date.now()
    if (remaining <= SOFT_CLOSE_THRESHOLD_MS) {
      setEndAt(new Date(Date.now() + SOFT_CLOSE_THRESHOLD_MS).toISOString())
      setExtended(true)
      window.setTimeout(() => setExtended(false), 4000)
    }
  }, [endAt])

  const pushBid = useCallback(
    (nickname: string, amount: number, isMine: boolean) => {
      seq.current += 1
      priceRef.current = amount
      setCurrentPrice(amount)
      setFlashKey((k) => k + 1)
      setBids((prev) => [
        {
          id: seq.current,
          bidderNickname: maskNickname(nickname),
          amount,
          createdAt: new Date().toISOString(),
          isMine,
        },
        ...prev,
      ])
      applySoftClose()
    },
    [applySoftClose],
  )

  /** 내 입찰 */
  const placeBid = useCallback(
    (amount: number, myNickname: string) => {
      if (amount < nextMin) return { ok: false as const, reason: 'TOO_LOW' as const }
      pushBid(myNickname, amount, true)
      return { ok: true as const }
    },
    [nextMin, pushBid],
  )

  /** 타 유저 입찰 시뮬레이션 (진행중일 때만) */
  useEffect(() => {
    if (auction.status !== 'LIVE') return
    const others = ['이서연', '박도현', '최지우', '정하윤', '강시우']
    const id = window.setInterval(
      () => {
        const price = priceRef.current
        const nick = others[seq.current % others.length]
        pushBid(nick, price + bidIncrement(price), false)
      },
      9000 + Math.floor((seq.current % 5) * 700),
    )
    return () => window.clearInterval(id)
  }, [auction.status, pushBid])

  return {
    currentPrice,
    startPrice: auction.startPrice,
    bids,
    endAt,
    extended,
    increment,
    nextMin,
    flashKey,
    placeBid,
  }
}
