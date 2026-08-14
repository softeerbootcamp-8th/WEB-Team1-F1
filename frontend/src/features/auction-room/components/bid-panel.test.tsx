import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { BidPanel } from './bid-panel'

// 세션 확인은 프로세스 밖으로 나가는 일이라 목으로 막는다, 입찰 자격은 역할과 판매자 여부만 본다
vi.mock('@/features/auth/auth-context', () => ({
  useAuth: () => ({
    user: { id: 1, realName: '김동현', role: 'GENERAL' },
    isAuthenticated: true,
    isLoading: false,
  }),
}))

const NOW = Date.now()

function renderPanel(
  endAt: string,
  overrides: { sellerIsMine?: boolean; increment?: number | null; nextMin?: number | null } = {},
) {
  const { sellerIsMine = false, increment = 100_000, nextMin = 10_100_000 } = overrides

  return render(
    <BidPanel
      currentPrice={10_000_000}
      increment={increment}
      nextMin={nextMin}
      sellerIsMine={sellerIsMine}
      endAt={endAt}
      clockOffset={0}
      onBid={vi.fn()}
    />,
  )
}

describe('BidPanel', () => {
  it('마감 전에는 입찰 버튼을 보여준다', () => {
    renderPanel(new Date(NOW + 60_000).toISOString())

    // 버튼이 고른 금액을 이름에 달고 있다, 마감 전에 폼이 열렸는지만 보므로 끝말로 잡는다
    expect(screen.getByRole('button', { name: /원 입찰$/ })).toBeTruthy()
  })

  // 서버가 거절할 요청을 화면이 먼저 막는다, 누를 수 있는 버튼을 눌러 실패를 받게 두지 않는다
  it('마감이 지나면 입찰 버튼 대신 마감 안내를 보여준다', () => {
    renderPanel(new Date(NOW - 1_000).toISOString())

    expect(screen.getByText('입찰이 마감됐습니다')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /원 입찰$/ })).toBeNull()
  })

  // 마감과 달리 기다린다고 열리지 않는다, 이 사람에게 호가 단위와 최소 입찰가는 쓸 데가 없다
  it('자기 차량을 보는 판매자에게는 최소 입찰가 안내까지 감춘다', () => {
    renderPanel(new Date(NOW + 60_000).toISOString(), { sellerIsMine: true })

    expect(screen.getByText('내가 내놓은 차량입니다')).toBeTruthy()
    expect(screen.queryByText(/최소 입찰가/)).toBeNull()
  })

  // 안내할 값이 아직 없다, 빈 자리를 줄표로 채워 보여 주지 않는다
  it('호가 단위를 받기 전에는 안내 문구만 남긴다', () => {
    renderPanel(new Date(NOW + 60_000).toISOString(), { increment: null, nextMin: null })

    expect(screen.getByText('호가 단위를 불러오는 중입니다.')).toBeTruthy()
    expect(screen.queryByText(/최소 입찰가/)).toBeNull()
  })

  // 로그인이나 다음 경매로 열릴 수 있는 갈래라 바닥 줄을 남긴다
  it('마감된 경매에는 최소 입찰가 안내를 남긴다', () => {
    renderPanel(new Date(NOW - 1_000).toISOString())

    expect(screen.getByText(/최소 입찰가/)).toBeTruthy()
  })
})
