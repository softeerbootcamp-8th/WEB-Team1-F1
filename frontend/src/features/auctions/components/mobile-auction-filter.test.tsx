import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'

import { EMPTY_FILTER } from '@/features/auctions/filter'

import { MobileAuctionFilter } from './mobile-auction-filter'

beforeAll(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
})

afterAll(() => vi.unstubAllGlobals())

function renderFilter({
  value = EMPTY_FILTER,
  status = null,
  onReset = vi.fn(),
}: {
  value?: typeof EMPTY_FILTER
  status?: 'LIVE' | 'SCHEDULED' | 'ENDED' | null
  onReset?: () => void
} = {}) {
  render(
    <MobileAuctionFilter
      value={value}
      onChange={vi.fn()}
      status={status}
      onStatusChange={vi.fn()}
      onReset={onReset}
    />,
  )

  return { onReset }
}

describe('MobileAuctionFilter', () => {
  it('긴 조건을 기본 화면에서 숨기고 필요할 때 패널로 연 뒤 실행 버튼으로 초점을 돌려준다', async () => {
    renderFilter()

    const trigger = screen.getByRole('button', { name: '필터 열기, 조건 없음' })
    trigger.focus()
    fireEvent.click(trigger)

    expect(screen.getByRole('dialog', { name: '경매 필터' })).not.toBeNull()
    expect(screen.getByRole('button', { name: '진행중' })).not.toBeNull()

    fireEvent.click(screen.getByRole('button', { name: '목록 보기' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '경매 필터' })).toBeNull())
    expect(document.activeElement).toBe(trigger)
  })

  it('차량 조건과 상태를 종류별로 세어 표시하고 바깥에서 한 번에 초기화한다', () => {
    const onReset = vi.fn()
    renderFilter({
      value: { ...EMPTY_FILTER, manufacturer: 'HYUNDAI', priceMin: 10_000_000 },
      status: 'LIVE',
      onReset,
    })

    expect(screen.getByText('3개 적용')).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: '필터 전체 초기화' }))

    expect(onReset).toHaveBeenCalledTimes(1)
  })
})
