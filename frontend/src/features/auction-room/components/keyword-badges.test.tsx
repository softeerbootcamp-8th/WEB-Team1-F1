import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { KeywordBadges } from './keyword-badges'

describe('경매방 키워드 줄', () => {
  it('서버가 준 순서 그대로 한국어 라벨을 그린다', () => {
    // 표시 순서는 서버가 정해 보낸다, 화면이 다시 정렬하면 목록과 어긋날 수 있다
    render(<KeywordBadges keywords={['NO_LEAK', 'CLEAN_INTERIOR']} />)

    const shown = screen.getAllByText(/무사고|없음|양호/).map((node) => node.textContent)

    expect(shown).toEqual(['누유 없음', '실내 상태 양호'])
  })

  it('키워드가 없는 차량은 줄 자체를 그리지 않는다', () => {
    const { container } = render(<KeywordBadges keywords={[]} />)

    expect(container.firstChild).toBeNull()
  })
})
