import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import type { CurveShape } from '../result'

import { PriceCurve } from './price-curve'

const ORIGIN_AT = '2026-08-03T10:50:00'
const END_AT = '2026-08-03T11:05:00'

// 내 입찰이 놓인 자리. 마지막 한 건(28)은 낙찰가 바로 아래라 라벨이 합쳐지는 자리다
const MINE = [3, 5, 8, 12, 17, 21, 28]

/** 마감으로 갈수록 값이 오른 서른 건. 곡선의 좌표는 curveShapeOf 가 이미 낸 값이라 여기선 직접 짠다 */
function shape(over: Partial<CurveShape> = {}): CurveShape {
  const points = Array.from({ length: 30 }, (_, index) => ({
    x: index / 29,
    y: index / 29,
    amount: 8_000_000 + (index + 1) * 100_000,
    mine: MINE.includes(index),
    extended: index >= 28,
  }))

  return {
    points,
    myLineY: null,
    minAmount: 8_000_000,
    maxAmount: 11_000_000,
    originAt: ORIGIN_AT,
    ...over,
  }
}

function circles(container: HTMLElement): SVGCircleElement[] {
  return Array.from(container.querySelectorAll('circle'))
}

function withClass(container: HTMLElement, token: string): SVGCircleElement[] {
  return circles(container).filter((circle) => circle.getAttribute('class')?.includes(token))
}

describe('PriceCurve', () => {
  it('입찰 서른 건이 점 서른 개로 남는다', () => {
    const { container } = render(
      <PriceCurve shape={shape()} endAt={END_AT} myAmount={null} mineWon={false} />,
    )

    // 남의 입찰은 빈 점(fill-card), 내 입찰은 채운 점이다. 지워 버리면 밀도가 사라진다
    expect(withClass(container, 'fill-card')).toHaveLength(30 - MINE.length)
    expect(withClass(container, 'fill-bid-mine')).toHaveLength(MINE.length)
  })

  it('가로축 왼쪽 눈금이 방이 열린 시각이 아니라 첫 입찰임을 밝힌다', () => {
    const { getByText } = render(
      <PriceCurve shape={shape()} endAt={END_AT} myAmount={null} mineWon={false} />,
    )

    expect(getByText(/첫 입찰 10:50/)).toBeTruthy()
  })

  it('내가 낙찰받으면 낙찰 점이 내 색을 지키고 낙찰은 링이 말한다', () => {
    const { container, getByText } = render(
      <PriceCurve
        shape={shape({ myLineY: 1 })}
        endAt={END_AT}
        myAmount={11_000_000}
        mineWon={true}
      />,
    )

    // 내 입찰 일곱 건에 낙찰 점이 하나 더해진다, 색이 초록으로 바뀌면 어느 점이 나인지 끊긴다
    expect(withClass(container, 'fill-bid-mine')).toHaveLength(MINE.length + 1)
    expect(getByText('낙찰 · 나')).toBeTruthy()

    const ring = circles(container).find(
      (circle) =>
        circle.getAttribute('r') === '9.5' &&
        circle.getAttribute('class')?.includes('stroke-price-up'),
    )
    expect(ring).toBeTruthy()
  })

  it('내 최고 입찰이 낙찰가에 붙어 라벨이 합쳐져도 그 점은 남는다', () => {
    const { container, getByText } = render(
      <PriceCurve
        shape={shape({ myLineY: 28 / 29 })}
        endAt={END_AT}
        myAmount={10_900_000}
        mineWon={false}
      />,
    )

    // 라벨은 낙찰 쪽으로 합치고, 점은 그대로 둔다 — 내가 어디까지 따라갔는지가 이 점이다
    expect(getByText(/내 입찰 1,090만/)).toBeTruthy()
    expect(withClass(container, 'fill-bid-mine')).toHaveLength(MINE.length)
  })

  it('시작가는 점이 아니라 바닥 눈금이 말한다', () => {
    const { getByText } = render(
      <PriceCurve shape={shape()} endAt={END_AT} myAmount={null} mineWon={false} />,
    )

    expect(getByText('시작가')).toBeTruthy()
    expect(getByText('800만')).toBeTruthy()
  })
})
