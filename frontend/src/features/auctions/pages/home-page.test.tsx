import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAuctionList } from '@/features/auctions/use-auction-list'

import { HomePage } from './home-page'

vi.mock('@/features/auctions/use-auction-list', () => ({
  useAuctionList: vi.fn(),
}))

describe('HomePage', () => {
  beforeEach(() => {
    vi.mocked(useAuctionList).mockClear()
    vi.stubGlobal(
      'matchMedia',
      vi.fn(() => ({ matches: true }) as MediaQueryList),
    )
  })

  it('실시간 목록을 구독하지 않고 경매 목록으로 가는 길만 남긴다', () => {
    render(
      <MemoryRouter>
        <HomePage />
      </MemoryRouter>,
    )

    expect(useAuctionList).not.toHaveBeenCalled()
    expect(
      screen.queryByRole('heading', { name: '지금 입찰 중인 차량' }),
    ).toBeNull()
    expect(
      screen.getByRole('link', { name: '진행 중인 경매' }).getAttribute('href'),
    ).toBe('/auctions')
  })
})
