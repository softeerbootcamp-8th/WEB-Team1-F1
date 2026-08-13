import type { ReactNode } from 'react'
import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { AuctionListCard, AuctionListPage } from '@/features/auctions/types'

import { useAuctionList } from './use-auction-list'

const SERVER_NOW = '2026-08-03T12:00:00.000Z'

function card(auctionId: number): AuctionListCard {
  return {
    auctionId,
    phase: 'LIVE',
    thumbnailUrl: null,
    manufacturer: 'HYUNDAI',
    model: `차 ${auctionId}`,
    modelYear: 2022,
    mileage: 30_000,
    keywords: [],
    startPrice: 10_000_000,
    currentPrice: 10_000_000,
    openAt: '2026-08-03T11:30:00.000Z',
    startAt: '2026-08-03T11:50:00.000Z',
    endAt: '2026-08-03T12:30:00.000Z',
    connectedCount: 0,
  }
}

function page(cards: AuctionListCard[], hasNext: boolean): AuctionListPage {
  return {
    content: cards,
    serverTime: SERVER_NOW,
    hasNext,
    nextCursor: hasNext
      ? {
          snapshotAt: SERVER_NOW,
          sortPriority: 0,
          sortAt: SERVER_NOW,
          auctionId: cards[cards.length - 1].auctionId,
        }
      : null,
  }
}

// HTTP 와 SSE 는 프로세스 밖으로 나가는 일이라 목으로 막는다, 검증 대상은 탭을 다시 볼 때 훅이 무엇을 하는지다
vi.mock('@/features/auctions/api', () => ({
  fetchAuctionList: vi.fn(async ({ cursor }: { cursor?: unknown }) =>
    cursor ? page([card(3), card(4)], false) : page([card(1), card(2)], true),
  ),
  subscribeAuctionListStream: vi.fn(() => () => {}),
}))

function switchTab(state: 'hidden' | 'visible') {
  vi.spyOn(document, 'visibilityState', 'get').mockReturnValue(state)
  document.dispatchEvent(new Event('visibilitychange'))
}

// 훅이 진입 방식을 주소에서 읽는다, 라우터 없이는 마운트되지 않는다
const wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter>{children}</MemoryRouter>

describe('useAuctionList 의 탭 복귀 갱신', () => {
  it('탭에 다녀와도 이어 읽은 목록과 스켈레톤 없는 화면을 유지한다', async () => {
    const { result } = renderHook(() => useAuctionList({ scope: 'ALL', filter: null }), {
      wrapper,
    })

    await waitFor(() => expect(result.current.cards).toHaveLength(2))
    await act(() => result.current.loadMore())
    expect(result.current.cards).toHaveLength(4)

    act(() => switchTab('hidden'))
    await act(async () => {
      switchTab('visible')
    })

    // 첫 페이지를 다시 읽어도 이어 읽어 둔 뒷 페이지는 남는다
    await waitFor(() => expect(result.current.cards).toHaveLength(4))
    // 목록이 이미 있는 갱신은 스켈레톤을 띄우지 않는다
    expect(result.current.isLoading).toBe(false)
  })
})
