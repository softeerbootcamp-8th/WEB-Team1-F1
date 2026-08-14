import { fireEvent, render, screen, waitFor } from '@testing-library/react'
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

// 서버가 내려주는 ProblemDetail 모양, getErrorCode 가 response.data.code 를 읽는다
function amountTooLow() {
  return {
    response: {
      status: 409,
      data: { code: 'BID_AMOUNT_TOO_LOW', detail: '입찰 금액이 최소 금액보다 낮습니다.' },
    },
  }
}

function renderPanel(
  endAt: string,
  overrides: {
    sellerIsMine?: boolean
    increment?: number | null
    nextMin?: number | null
    onBid?: (amount: number) => Promise<void>
  } = {},
) {
  const {
    sellerIsMine = false,
    increment = 100_000,
    nextMin = 10_100_000,
    onBid = vi.fn(),
  } = overrides

  const panel = (props: { nextMin: number | null }) => (
    <BidPanel
      currentPrice={10_000_000}
      increment={increment}
      nextMin={props.nextMin}
      sellerIsMine={sellerIsMine}
      endAt={endAt}
      clockOffset={0}
      onBid={onBid}
    />
  )

  const view = render(panel({ nextMin }))

  // 다른 사람의 입찰이 SSE로 들어와 최소 입찰가가 오른 상황을 같은 폼에 다시 그려 재현한다
  return { ...view, raiseNextMin: (raised: number) => view.rerender(panel({ nextMin: raised })) }
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

  it('상한에 닿으면 입찰가를 더 올릴 수 없다', () => {
    renderPanel(new Date(NOW + 60_000).toISOString(), {
      increment: 100_000,
      nextMin: 999_999_950_000,
    })

    expect(screen.getByRole('button', { name: '입찰가 높이기' }).hasAttribute('disabled')).toBe(
      true,
    )
  })

  // 로그인이나 다음 경매로 열릴 수 있는 갈래라 바닥 줄을 남긴다
  it('마감된 경매에는 최소 입찰가 안내를 남긴다', () => {
    renderPanel(new Date(NOW - 1_000).toISOString())

    expect(screen.getByText(/최소 입찰가/)).toBeTruthy()
  })

  // 고른 금액이 손가락 아래에서 오르면 의도한 적 없는 금액이 나간다
  it('최소 입찰가가 올라도 고른 금액을 그대로 보낸다', async () => {
    const onBid = vi.fn().mockResolvedValue(undefined)
    const { raiseNextMin } = renderPanel(new Date(NOW + 60_000).toISOString(), { onBid })

    raiseNextMin(10_200_000)
    fireEvent.click(screen.getByRole('button', { name: /원 입찰$/ }))

    await waitFor(() => expect(onBid).toHaveBeenCalledWith(10_100_000))
  })

  // 판정은 서버가 한다, 화면은 그 사유를 받아 무엇이 막았고 얼마부터 되는지로 옮긴다
  it('금액이 낮아 거절되면 현재가와 최소 입찰가를 알리고 맞추기를 낸다', async () => {
    const onBid = vi.fn().mockRejectedValue(amountTooLow())
    const { raiseNextMin } = renderPanel(new Date(NOW + 60_000).toISOString(), { onBid })

    fireEvent.click(screen.getByRole('button', { name: /원 입찰$/ }))

    // 거절과 함께 새 현재가가 닿은 상태다, 스트림이 늦으면 서버 문구가 그대로 남는다
    raiseNextMin(10_200_000)

    await waitFor(() =>
      expect(
        screen.getByText('현재가가 이미 10,000,000원입니다, 최소 입찰가는 10,200,000원입니다.'),
      ).toBeTruthy(),
    )

    fireEvent.click(screen.getByRole('button', { name: /으로 맞추기$/ }))
    fireEvent.click(screen.getByRole('button', { name: /원 입찰$/ }))

    await waitFor(() => expect(onBid).toHaveBeenLastCalledWith(10_200_000))
  })
})
