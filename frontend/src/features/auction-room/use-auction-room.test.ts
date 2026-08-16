import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { AuctionRoomView, RoomStreamState } from '@/features/auction-room/types'

import { useAuctionRoom } from './use-auction-room'

const OPEN_AT = '2026-08-03T18:00:00.000Z'
const START_AT = '2026-08-03T18:30:00.000Z'
const END_AT = '2026-08-03T18:50:00.000Z'
const EXTENDED_END_AT = '2026-08-03T18:53:00.000Z'
// 마감 10초 전에 들어간다, 원래 마감을 지나 보내는 데 오래 기다리지 않는다
const SERVER_NOW = '2026-08-03T18:49:50.000Z'

// 구독 콜백을 붙잡아 두었다가 테스트가 연장된 현황을 직접 밀어 넣는다
let pushState: ((state: RoomStreamState) => void) | null = null

function room(endAt: string): AuctionRoomView {
  return {
    auctionId: 1,
    phase: 'LIVE',
    vehicle: {
      manufacturer: 'HYUNDAI',
      model: '더 뉴 셀토스',
      modelYear: 2022,
      mileage: 35_000,
      fuelType: 'GASOLINE',
      keywords: [],
      imageUrls: [],
      diagnosticReportUrl: 'https://cdn.race.dev/report.pdf',
    },
    startPrice: 10_000_000,
    currentPrice: 10_000_000,
    openAt: OPEN_AT,
    startAt: START_AT,
    endAt,
    serverTime: SERVER_NOW,
    viewerCount: 1,
    bidderCount: 0,
    bidCount: 0,
    winner: null,
    sellerIsMine: false,
    recentBids: [],
  }
}

function streamState(endAt: string): RoomStreamState {
  return {
    auctionId: 1,
    phase: 'LIVE',
    currentPrice: 10_100_000,
    endAt,
    serverTime: SERVER_NOW,
    viewerCount: 1,
    bidderCount: 1,
    bidCount: 1,
    winner: null,
    recentBids: [],
  }
}

// HTTP 와 SSE 는 프로세스 밖으로 나가는 일이라 목으로 막는다, 검증 대상은 훅이 마감을 언제 세는지다
vi.mock('@/features/auction-room/api', () => ({
  fetchAuctionRoom: vi.fn(async () => room(END_AT)),
  fetchBidIncrementBands: vi.fn(async () => []),
  fetchRoomOpening: vi.fn(),
  placeBid: vi.fn(),
  subscribeRoomStream: vi.fn((_auctionId: number, onState: (state: RoomStreamState) => void) => {
    pushState = onState
    return () => {}
  }),
}))

describe('useAuctionRoom 마감 처리', () => {
  beforeEach(() => {
    // 훅이 Date.now() 와 setTimeout 을 함께 쓰므로 둘이 같은 시계를 봐야 한다
    vi.useFakeTimers({ shouldAdvanceTime: true })
    vi.setSystemTime(new Date(SERVER_NOW))
    pushState = null
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  // 마감 임박 입찰은 마감을 뒤로 민다, 화면이 원래 마감에 나가면 진행 중 경매에서 사람을 내보낸다
  it('마감이 밀리면 원래 마감 시각을 지나도 방에 남아 있는다', async () => {
    const { result } = renderHook(() => useAuctionRoom(1))
    await waitFor(() => expect(result.current.entry).toBe('OPEN'))

    act(() => pushState?.(streamState(EXTENDED_END_AT)))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000)
    })

    expect(result.current.entry).toBe('OPEN')
  })

  it('밀린 마감이 지나면 결과로 넘어간다', async () => {
    const { result } = renderHook(() => useAuctionRoom(1))
    await waitFor(() => expect(result.current.entry).toBe('OPEN'))

    act(() => pushState?.(streamState(EXTENDED_END_AT)))

    await act(async () => {
      await vi.advanceTimersByTimeAsync(200_000)
    })

    expect(result.current.entry).toBe('CLOSED')
  })
})
