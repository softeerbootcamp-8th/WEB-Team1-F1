import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { BidIncrementHelp } from './bid-increment-help'

const BANDS = [
  { minPrice: 0, increment: 100_000 },
  { minPrice: 10_000_000, increment: 500_000 },
  { minPrice: 30_000_000, increment: 1_000_000 },
]

describe('입찰 기준 도움말', () => {
  it('서버가 준 구간을 가격 오름차순으로 그린다', () => {
    // 순서가 섞이면 "가격대가 올라가면 상승가도 커진다"가 표에서 읽히지 않는다
    render(<BidIncrementHelp bands={[...BANDS].reverse()} currentPrice={0} />)

    const rows = screen.getAllByRole('row').slice(1)
    const increments = rows.map(
      (row) => within(row).getAllByRole('cell')[1].textContent,
    )

    expect(increments).toEqual(['10만', '50만', '100만'])
  })

  it('현재 가격이 속한 구간을 표시한다', () => {
    // 강조 판정은 입찰 패널이 상승가를 구할 때 쓰는 함수와 같은 것이라,
    // 이 기대가 깨지면 패널이 안내하는 금액과 표가 어긋난 것이다
    render(<BidIncrementHelp bands={BANDS} currentPrice={12_000_000} />)

    const current = screen.getByText('(현재 구간)').closest('tr')

    expect(within(current!).getAllByRole('cell')[1].textContent).toBe('50만')
  })

  it('구간표를 아직 받지 못했으면 표 대신 상태를 알린다', () => {
    // 빈 표를 그리면 "상승가가 없다"로 읽힌다
    render(<BidIncrementHelp bands={[]} currentPrice={5_000_000} />)

    expect(screen.getByRole('status').textContent).toContain(
      '기준을 불러오는 중입니다',
    )
    expect(screen.queryByRole('table')).toBeNull()
  })
})
