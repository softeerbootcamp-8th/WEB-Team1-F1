import { useId } from 'react'

import type { CurveShape } from '@/features/auction-room/result'
import { formatManwon } from '@/lib/format'

// viewBox 좌표. 실제 크기는 부모가 정하고 이 안은 비율로만 그린다
const WIDTH = 640
const HEIGHT = 260

// 좌우가 다른 이유는 왼쪽만 금액 라벨 자리를 비워야 해서다, 같은 값을 주면 오른쪽이 그만큼 빈다
const PAD_LEFT = 56
const PAD_RIGHT = 16
const PAD_TOP = 28
const PAD_BOTTOM = 32

// 점을 다 찍으면 뭉치는 경계. 마감 임박 호가는 몇 초 간격으로 들어와 자리가 겹친다
const DOT_LIMIT = 12

// 라벨 한 줄 높이. 두 값이 이보다 가까우면 각각 적어도 서로를 덮는다
const LABEL_LINE_HEIGHT = 14

interface PriceCurveProps {
  shape: CurveShape
  /** 곡선 아래에 적을 시각 눈금, 시작과 끝 둘 */
  startAt: string
  endAt: string
  /** 내 최고 입찰, 입찰한 적이 없으면 null */
  myAmount: number | null
  /** 내가 낙찰자인지. 낙찰 자리와 내 최고가가 같은 점이라 표시를 하나로 합친다 */
  mineWon: boolean
}

/**
 * 가격이 오른 과정을 그리는 선.
 *
 * 좌표 계산은 curveShapeOf 가 이미 끝냈다. 여기서 하는 일은 0에서 1 사이의 값을 viewBox 안의
 * 자리로 옮기는 것뿐이라, 눈금이 어떻게 정해졌는지는 이 파일이 몰라도 된다.
 */
export function PriceCurve({ shape, startAt, endAt, myAmount, mineWon }: PriceCurveProps) {
  // 한 화면에 곡선이 둘 있어도 그라데이션이 서로를 덮지 않게 한다
  const fillId = useId()

  const x = (value: number) => PAD_LEFT + value * (WIDTH - PAD_LEFT - PAD_RIGHT)
  // y 는 0이 바닥이라 위아래를 뒤집어 화면 좌표로 옮긴다
  const y = (value: number) => HEIGHT - PAD_BOTTOM - value * (HEIGHT - PAD_TOP - PAD_BOTTOM)

  const first = shape.points[0]
  const last = shape.points[shape.points.length - 1]

  const path = shape.points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${x(point.x)} ${y(point.y)}`)
    .join(' ')

  // 선 아래를 옅게 채운다. 같은 경로에 바닥을 붙여 닫는다
  const area = `${path} V ${y(0)} H ${x(first.x)} Z`

  // 낙찰가와 내 최고가가 이만큼도 안 떨어져 있으면 마커 둘로 가를 수 없다. 라벨을 합친다
  const mergedLabel =
    mineWon ||
    (myAmount !== null &&
      shape.myLineY !== null &&
      Math.abs(y(shape.myLineY) - y(last.y)) < LABEL_LINE_HEIGHT)

  const winnerLabel = mineWon
    ? '낙찰 · 나'
    : mergedLabel && myAmount !== null
      ? `낙찰 ${formatManwon(last.amount)} · 내 입찰 ${formatManwon(myAmount)}`
      : '낙찰'

  // 글자 폭을 재지 않고 어림한다. 오른쪽 끝에서 잘리는 것만 막으면 된다
  const labelFitsCentered = x(last.x) + (mergedLabel ? 130 : 26) / 2 <= WIDTH - PAD_RIGHT

  // 뭉치면 남의 입찰 점을 지운다. 내가 부른 자리는 몇 건이든 남겨야 내가 어디까지 따라갔는지 보인다.
  // 라벨을 합친 경우에는 내 최고가 점도 뺀다, 낙찰 점과 같은 자리라 서로를 덮는다
  const dots = (
    shape.points.length > DOT_LIMIT
      ? shape.points.filter(
          (point, index) => point.mine || index === 0 || index === shape.points.length - 1,
        )
      : shape.points
  ).filter((point) => !(mergedLabel && point.mine && point.amount === myAmount))

  // 폭이 0인 경매는 가운데 눈금이 위아래와 같은 금액이라 세 줄이 같은 값을 말하게 된다
  const ticks = [
    { value: 1, amount: shape.maxAmount },
    ...(shape.maxAmount === shape.minAmount
      ? []
      : [{ value: 0.5, amount: Math.round((shape.minAmount + shape.maxAmount) / 2) }]),
    { value: 0, amount: shape.minAmount },
  ]

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="w-full"
      role="img"
      aria-label={`시작가 ${formatManwon(shape.minAmount)}에서 ${formatManwon(shape.maxAmount)}까지 오른 과정`}
    >
      <defs>
        <linearGradient id={fillId} className="text-price-up" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="currentColor" stopOpacity={0.22} />
          <stop offset="100%" stopColor="currentColor" stopOpacity={0} />
        </linearGradient>
      </defs>

      <path d={area} fill={`url(#${fillId})`} stroke="none" />

      {/* 눈금선과 금액. 가운데는 값을 읽는 보조선이라 옅게 둔다 */}
      {ticks.map((tick) => (
        <g key={tick.value}>
          <line
            x1={PAD_LEFT}
            x2={WIDTH - PAD_RIGHT}
            y1={y(tick.value)}
            y2={y(tick.value)}
            className="stroke-border"
            strokeWidth={1}
            strokeOpacity={tick.value === 0.5 ? 0.5 : 1}
          />
          <text
            x={PAD_LEFT - 8}
            y={y(tick.value) + 4}
            textAnchor="end"
            className="fill-muted-foreground text-[11px]"
          >
            {formatManwon(tick.amount)}
          </text>
        </g>
      ))}

      {/* 내 최고 입찰선. 곡선과 견주라고 있는 선이라 점선으로 둔다 */}
      {shape.myLineY !== null && myAmount !== null && !mergedLabel && (
        <g>
          <line
            x1={PAD_LEFT}
            x2={WIDTH - PAD_RIGHT}
            y1={y(shape.myLineY)}
            y2={y(shape.myLineY)}
            className="stroke-destructive"
            strokeWidth={1.5}
            strokeDasharray="6 4"
          />
          {/* 라벨은 왼쪽 끝이다. 오른쪽에 두면 내 값이 낙찰가에 붙었을 때 낙찰 라벨과 겹친다 */}
          <text
            x={PAD_LEFT + 6}
            y={y(shape.myLineY) - 6}
            className="fill-destructive text-[11px] font-medium"
          >
            내 입찰 {formatManwon(myAmount)}
          </text>
        </g>
      )}

      <path
        d={path}
        fill="none"
        className="stroke-price-up"
        strokeWidth={2.5}
        strokeLinejoin="round"
        strokeLinecap="round"
      />

      {dots.map((point, index) => (
        <circle
          key={index}
          cx={x(point.x)}
          cy={y(point.y)}
          r={point.mine ? 5.5 : 4.5}
          strokeWidth={2}
          className={
            point.mine ? 'fill-destructive stroke-destructive' : 'fill-card stroke-price-up'
          }
        />
      ))}

      {/* 마지막 점만 채워 낙찰 자리를 표시한다 */}
      <g>
        <circle cx={x(last.x)} cy={y(last.y)} r={5.5} className="fill-price-up stroke-price-up" />
        <text
          x={labelFitsCentered ? x(last.x) : WIDTH - PAD_RIGHT}
          y={y(last.y) - 12}
          textAnchor={labelFitsCentered ? 'middle' : 'end'}
          className="fill-price-up text-[11px] font-semibold"
        >
          {winnerLabel}
        </text>
      </g>

      {/* 가로축은 시작에서 마감까지다, 가운데 눈금이 있어야 오른쪽 여백이 남은 시간으로 읽힌다 */}
      {[
        { at: startAt, x: 0, anchor: 'start' as const },
        { at: middleOf(startAt, endAt), x: 0.5, anchor: 'middle' as const },
        { at: endAt, x: 1, anchor: 'end' as const },
      ].map((tick) => (
        <text
          key={tick.x}
          x={x(tick.x)}
          y={HEIGHT - 8}
          textAnchor={tick.anchor}
          className="fill-muted-foreground text-[11px]"
        >
          {clock(tick.at)}
        </text>
      ))}
    </svg>
  )
}

function middleOf(from: string, to: string): string {
  const mid = (new Date(from).getTime() + new Date(to).getTime()) / 2

  return new Date(mid).toISOString()
}

// 곡선 눈금은 오전·오후 없이 시:분만 쓴다, 양 끝이 같은 날 20분 안쪽이라 구분이 필요 없다
function clock(iso: string): string {
  return new Date(iso).toLocaleTimeString('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}
