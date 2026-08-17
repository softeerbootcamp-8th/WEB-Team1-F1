import type { RoomResultView } from '@/features/auction-room/types'

/**
 * 결과 화면에서 보는 사람이 선 자리. 첫 문장이 이것으로 갈린다.
 *
 * 경매가 낙찰인지 유찰인지는 여기 섞지 않는다. 그건 보는 사람과 무관한 사실이라
 * outcome 이 따로 답한다. 둘을 한 값에 뭉치면 갈래가 여덟이 되고 화면이 그만큼 갈라진다.
 */
export type ViewerStanding = 'SELLER' | 'WON' | 'LOST' | 'ONLOOKER'

/**
 * 서버가 판정해 준 값 셋으로 자리를 정한다.
 *
 * 서버가 자기 차 입찰을 막으므로 판매자가 낙찰자일 수는 없다. 그래도 판매자를 먼저 보는 것은
 * 그 규칙이 흔들려도 판매자에게는 "낙찰받았어요"보다 "내 차가 팔렸어요"가 맞는 말이기 때문이다.
 */
export function viewerStandingOf(result: RoomResultView): ViewerStanding {
  if (result.sellerIsMine) return 'SELLER'
  if (result.winner?.mine) return 'WON'
  if (result.myStanding) return 'LOST'

  return 'ONLOOKER'
}

/** 낙찰 결과에서 보는 사람의 거래 목록으로 보낸다. 판매·구매 탭을 섞지 않는다. */
export function dealListPathOf(standing: ViewerStanding): string | null {
  if (standing === 'SELLER') return '/mypage/sales'
  if (standing === 'WON') return '/mypage/purchases'
  return null
}

/** 그리기 좌표는 0에서 1 사이로 낸다. 실제 크기는 그리는 쪽이 정한다 */
export interface CurvePoint {
  x: number
  /** 0이 시작가, 1이 낙찰가 */
  y: number
  amount: number
  mine: boolean
  /** 이 입찰로 마감이 밀렸는지, 앞에 붙는 시작가 점은 입찰이 아니라 항상 거짓이다 */
  extended: boolean
}

export interface CurveShape {
  points: CurvePoint[]
  /** 내 최고 입찰선의 높이, 입찰한 적이 없으면 없다 */
  myLineY: number | null
  minAmount: number
  maxAmount: number
}

/**
 * 가격이 오른 과정을 그릴 좌표. 입찰이 한 건도 없으면 그릴 선이 없어 null 이다.
 *
 * 서버는 입찰만 준다. 곡선의 첫 점인 시작가는 여기서 앞에 붙인다.
 */
export function curveShapeOf(result: RoomResultView): CurveShape | null {
  if (result.priceCurve.length === 0) return null

  const amounts = [result.startPrice, ...result.priceCurve.map((p) => p.amount)]
  const times = [result.startAt, ...result.priceCurve.map((p) => p.bidAt)].map((at) =>
    new Date(at).getTime(),
  )

  const minAmount = Math.min(...amounts)
  const maxAmount = Math.max(...amounts)

  // 가로는 마지막 입찰이 아니라 마감까지다. 축 라벨이 시작과 마감이라 여기를 마지막 입찰로 잡으면
  // 라벨과 선 끝이 다른 시각을 가리킨다. 남는 오른쪽 여백이 곧 아무도 부르지 않은 시간이다
  const startTime = new Date(result.startAt).getTime()
  const spanX = new Date(result.endAt).getTime() - startTime

  // 세로 폭이 0인 경매도 있다. 첫 입찰의 하한이 시작가라(BidIncrementTable.ruleFor)
  // 시작가와 같은 금액 한 건으로 끝날 수 있고, 그때는 모든 점이 같은 높이에 놓인다
  const spanY = maxAmount - minAmount

  const heightOf = (amount: number) => (spanY === 0 ? 0 : (amount - minAmount) / spanY)

  const points = amounts.map((amount, index) => ({
    x: (times[index] - startTime) / spanX,
    y: heightOf(amount),
    amount,
    mine: index === 0 ? false : result.priceCurve[index - 1].mine,
    extended: index === 0 ? false : result.priceCurve[index - 1].extended,
  }))

  return {
    points,
    myLineY: result.myStanding ? heightOf(result.myStanding.highestAmount) : null,
    minAmount,
    maxAmount,
  }
}
