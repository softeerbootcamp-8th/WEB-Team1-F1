import { describe, expect, it } from 'vitest'

import type { PricePoint, RoomResultView } from '@/features/auction-room/types'

import { curveShapeOf, viewerStandingOf } from './result'

const START_AT = '2026-08-03T10:45:00'
const END_AT = '2026-08-03T11:05:00'

function result(over: Partial<RoomResultView> = {}): RoomResultView {
  return {
    auctionId: 1,
    outcome: 'SOLD',
    vehicle: {
      manufacturer: 'HYUNDAI',
      model: '캐스퍼',
      modelYear: 2022,
      mileage: 41_000,
      fuelType: 'GASOLINE',
      imageUrls: [],
      diagnosticReportUrl: 'https://cdn.race.dev/casper.pdf',
    },
    startPrice: 8_000_000,
    winningPrice: 10_100_000,
    winner: { name: '정*찰', mine: false },
    sellerIsMine: false,
    myBid: null,
    bidCount: 4,
    bidderCount: 4,
    extensionCount: 1,
    startAt: START_AT,
    endAt: END_AT,
    resultEndAt: '2026-08-03T11:10:00',
    serverTime: '2026-08-03T11:05:48',
    priceCurve: [],
    ...over,
  }
}

// 서버는 시간대 없는 문자열을 준다. toISOString 으로 만들면 UTC 로 밀려 startAt 과 어긋난다
function point(minutesFromStart: number, amount: number, mine = false): PricePoint {
  const at = new Date(START_AT)
  at.setMinutes(at.getMinutes() + minutesFromStart)

  const pad = (value: number) => String(value).padStart(2, '0')
  const date = `${at.getFullYear()}-${pad(at.getMonth() + 1)}-${pad(at.getDate())}`
  const time = `${pad(at.getHours())}:${pad(at.getMinutes())}:${pad(at.getSeconds())}`

  return { at: `${date}T${time}`, amount, mine }
}

// 화면의 첫 문장이 여기서 갈린다. 네 갈래가 각각 다른 말을 해야 한다
describe('viewerStandingOf', () => {
  it('낙찰자는 자기가 받았다는 것을 안다', () => {
    expect(viewerStandingOf(result({ winner: { name: '정*찰', mine: true } }))).toBe('WON')
  })

  it('입찰했지만 낙찰자가 아니면 놓친 것이다', () => {
    expect(viewerStandingOf(result({ myBid: { amount: 9_300_000, rank: 2 } }))).toBe('LOST')
  })

  it('차를 내놓은 사람은 입찰과 무관하게 판매자다', () => {
    expect(viewerStandingOf(result({ sellerIsMine: true }))).toBe('SELLER')
  })

  // 지금은 자기 차에 입찰하는 것을 막지 않는다(#163). 그 사람에게는 낙찰보다 판매가 앞선 사실이다
  it('자기 차를 자기가 받아도 판매자로 본다', () => {
    const selfDealt = result({ sellerIsMine: true, winner: { name: '최*매', mine: true } })

    expect(viewerStandingOf(selfDealt)).toBe('SELLER')
  })

  it('입찰도 안 했고 파는 사람도 아니면 구경꾼이다', () => {
    expect(viewerStandingOf(result())).toBe('ONLOOKER')
  })

  // 유찰은 입찰이 한 건도 없을 때만 나오므로 갈래는 판매자와 구경꾼 둘뿐이다
  it('유찰이어도 판매자와 구경꾼은 갈린다', () => {
    const unsold = { outcome: 'UNSOLD', winningPrice: null, winner: null } as const

    expect(viewerStandingOf(result({ ...unsold, sellerIsMine: true }))).toBe('SELLER')
    expect(viewerStandingOf(result({ ...unsold }))).toBe('ONLOOKER')
  })
})

describe('curveShapeOf', () => {
  const curve = [
    point(0, 8_500_000),
    point(10, 9_300_000, true),
    point(15, 9_800_000),
    point(20, 10_100_000),
  ]

  it('서버가 주지 않는 시작가 점을 앞에 붙인다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    // 이미지의 곡선은 800만에서 출발한다, 그 점은 입찰이 아니라 시작가다
    expect(shape?.points[0].amount).toBe(8_000_000)
    expect(shape?.points[0].mine).toBe(false)
    expect(shape?.points).toHaveLength(curve.length + 1)
  })

  it('가로는 첫 점에서 끝 점까지 꽉 채운다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    expect(shape?.points.at(0)?.x).toBe(0)
    expect(shape?.points.at(-1)?.x).toBe(1)
  })

  it('세로는 시작가가 바닥이고 낙찰가가 꼭대기다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    expect(shape?.points.at(0)?.y).toBe(0)
    expect(shape?.points.at(-1)?.y).toBe(1)
  })

  it('내 최고 입찰선은 그 금액의 높이에 놓이고, 입찰하지 않았으면 없다', () => {
    const mine = curveShapeOf(result({ priceCurve: curve, myBid: { amount: 9_300_000, rank: 2 } }))
    const watching = curveShapeOf(result({ priceCurve: curve }))

    // 800만에서 1010만 사이의 930만이라 바닥도 꼭대기도 아니다
    expect(mine?.myLineY).toBeGreaterThan(0)
    expect(mine?.myLineY).toBeLessThan(1)
    expect(watching?.myLineY).toBeNull()
  })

  // 첫 입찰의 하한은 시작가라(BidIncrementTable.ruleFor) 시작가와 같은 금액으로 한 건만 들어올 수 있다
  it('입찰이 시작가와 같아 값이 오르지 않았어도 내 입찰선은 남는다', () => {
    const flat = result({
      startPrice: 8_000_000,
      winningPrice: 8_000_000,
      priceCurve: [point(3, 8_000_000, true)],
      myBid: { amount: 8_000_000, rank: 1 },
    })

    expect(curveShapeOf(flat)?.myLineY).toBe(0)
  })

  it('입찰이 없으면 그릴 선이 없다', () => {
    expect(curveShapeOf(result({ outcome: 'UNSOLD', winner: null, priceCurve: [] }))).toBeNull()
  })

  // 시각 폭이 0이면 나눗셈이 NaN 이 되어 선이 통째로 사라진다
  it('입찰이 한 건이면 시각 폭이 없어도 좌표가 무너지지 않는다', () => {
    const shape = curveShapeOf(result({ priceCurve: [point(0, 10_100_000)] }))

    expect(shape?.points.every((p) => Number.isFinite(p.x) && Number.isFinite(p.y))).toBe(true)
    expect(shape?.points.at(-1)?.x).toBe(1)
  })
})
