import { describe, expect, it } from 'vitest'

import type { PricePoint, RoomResultView } from '@/features/auction-room/types'

import { curveShapeOf, dealListPathOf, viewerStandingOf } from './result'

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
      keywords: [],
      imageUrls: [],
      diagnosticReportUrl: 'https://cdn.race.dev/casper.pdf',
    },
    startPrice: 8_000_000,
    winningPrice: 10_100_000,
    winner: { name: '정*찰', mine: false },
    sellerIsMine: false,
    myStanding: null,
    bidCount: 4,
    bidderCount: 4,
    extensionCount: 1,
    startAt: START_AT,
    endAt: END_AT,
    resultViewingEndsAt: '2026-08-03T11:10:00',
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

  return { bidAt: `${date}T${time}`, amount, mine, extended: false }
}

// 화면의 첫 문장이 여기서 갈린다. 네 갈래가 각각 다른 말을 해야 한다
describe('viewerStandingOf', () => {
  it('낙찰자는 자기가 받았다는 것을 안다', () => {
    expect(viewerStandingOf(result({ winner: { name: '정*찰', mine: true } }))).toBe('WON')
  })

  it('입찰했지만 낙찰자가 아니면 놓친 것이다', () => {
    expect(viewerStandingOf(result({ myStanding: { highestAmount: 9_300_000, rank: 2 } }))).toBe('LOST')
  })

  it('차를 내놓은 사람은 입찰과 무관하게 판매자다', () => {
    expect(viewerStandingOf(result({ sellerIsMine: true }))).toBe('SELLER')
  })

  // 서버가 막으므로 실제로는 나올 수 없는 조합이다. 그 규칙이 흔들려도 판매가 앞서는지를 여기서 고정한다
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

describe('dealListPathOf', () => {
  it('판매자와 낙찰자를 각자의 거래 내역으로 보낸다', () => {
    expect(dealListPathOf('SELLER')).toBe('/mypage/sales')
    expect(dealListPathOf('WON')).toBe('/mypage/purchases')
  })

  it('거래가 없는 미낙찰 사용자와 구경꾼은 거래 내역 링크가 없다', () => {
    expect(dealListPathOf('LOST')).toBeNull()
    expect(dealListPathOf('ONLOOKER')).toBeNull()
  })
})

describe('curveShapeOf', () => {
  const curve = [
    point(0, 8_500_000),
    point(10, 9_300_000, true),
    point(15, 9_800_000),
    // 마감 정각에는 입찰이 성립하지 않고, 임박 입찰은 소프트클로즈가 마감을 밀어낸다
    // 그래서 마지막 입찰은 늘 마감보다 앞선다
    point(19, 10_100_000),
  ]

  it('서버가 주지 않는 시작가 점을 앞에 붙인다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    // 이미지의 곡선은 800만에서 출발한다, 그 점은 입찰이 아니라 시작가다
    expect(shape?.points[0].amount).toBe(8_000_000)
    expect(shape?.points[0].mine).toBe(false)
    expect(shape?.points).toHaveLength(curve.length + 1)
  })

  // 가로축 라벨이 시작과 마감이므로 점도 그 사이에 놓여야 한다, 마지막 입찰을 끝에 붙이면 라벨이 거짓이 된다
  it('가로는 시작에서 마감까지이고 마지막 입찰은 그 안쪽이다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    expect(shape?.points.at(0)?.x).toBe(0)
    expect(shape?.points.at(-1)?.x).toBeLessThan(1)
    expect(shape?.points.at(-1)?.x).toBeGreaterThan(0.9)
  })

  it('세로는 시작가가 바닥이고 낙찰가가 꼭대기다', () => {
    const shape = curveShapeOf(result({ priceCurve: curve }))

    expect(shape?.points.at(0)?.y).toBe(0)
    expect(shape?.points.at(-1)?.y).toBe(1)
  })

  it('내 최고 입찰선은 그 금액의 높이에 놓이고, 입찰하지 않았으면 없다', () => {
    const mine = curveShapeOf(result({ priceCurve: curve, myStanding: { highestAmount: 9_300_000, rank: 2 } }))
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
      myStanding: { highestAmount: 8_000_000, rank: 1 },
    })

    expect(curveShapeOf(flat)?.myLineY).toBe(0)
  })

  it('입찰이 없으면 그릴 선이 없다', () => {
    expect(curveShapeOf(result({ outcome: 'UNSOLD', winner: null, priceCurve: [] }))).toBeNull()
  })

  it('시작 직후에 한 건만 들어와도 좌표가 무너지지 않는다', () => {
    const shape = curveShapeOf(result({ priceCurve: [point(0, 10_100_000)] }))

    expect(shape?.points.every((p) => Number.isFinite(p.x) && Number.isFinite(p.y))).toBe(true)
    expect(shape?.points.at(-1)?.x).toBe(0)
  })

  // 마감이 밀린 자리를 그리려면 어느 점이 마감을 밀었는지가 좌표까지 따라와야 한다
  it('마감을 밀어낸 입찰 표시를 좌표에 실어 보낸다', () => {
    const shape = curveShapeOf(
      result({
        priceCurve: [point(10, 9_300_000), { ...point(19, 10_100_000), extended: true }],
      }),
    )

    // 앞에 붙는 시작가 점은 입찰이 아니라 마감을 밀 수 없다
    expect(shape?.points.map((p) => p.extended)).toEqual([false, false, true])
  })
})
