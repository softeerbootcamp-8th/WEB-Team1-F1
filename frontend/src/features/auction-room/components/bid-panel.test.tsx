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

function renderPanel(endAt: string) {
  return render(
    <BidPanel
      currentPrice={10_000_000}
      increment={100_000}
      nextMin={10_100_000}
      sellerIsMine={false}
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
})
