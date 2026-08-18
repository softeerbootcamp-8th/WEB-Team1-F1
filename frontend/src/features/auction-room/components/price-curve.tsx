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

// 라벨 한 줄 높이. 두 값이 이보다 가까우면 각각 적어도 서로를 덮는다
const LABEL_LINE_HEIGHT = 14

interface PriceCurveProps {
  shape: CurveShape
  /** 곡선 아래 오른쪽 눈금. 왼쪽은 첫 입찰이라 shape 이 들고 있다 */
  endAt: string
  /** 내 최고 입찰, 입찰한 적이 없으면 null */
  myAmount: number | null
  /** 내가 낙찰자인지. 낙찰 자리와 내 최고가가 같은 점이라 라벨을 하나로 합친다 */
  mineWon: boolean
}

/**
 * 가격이 오른 과정을 그리는 선.
 *
 * 좌표 계산은 curveShapeOf 가 이미 끝냈다. 여기서 하는 일은 0에서 1 사이의 값을 viewBox 안의
 * 자리로 옮기는 것뿐이라, 눈금이 어떻게 정해졌는지는 이 파일이 몰라도 된다.
 */
export function PriceCurve({ shape, endAt, myAmount, mineWon }: PriceCurveProps) {
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

  const extendedAt = shape.points.filter((point) => point.extended)

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

  // 시작가 캡션은 내 입찰선 라벨과 같은 자리를 쓴다. 둘이 가까우면 내 값을 살린다.
  // 라벨을 합쳤으면 그 자리에 내 입찰선 라벨이 없어 캡션이 갈 곳이 남는다
  const startPriceLabelFits =
    mergedLabel ||
    shape.myLineY === null ||
    Math.abs(y(shape.myLineY) - y(0)) >= LABEL_LINE_HEIGHT

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

      {/* 마감이 밀린 자리. 값이 아니라 시간 축의 사건이라 세로로 긋고, 곡선보다 아래에 깔린다 */}
      {extendedAt.map((point, index) => (
        <g key={index}>
          <line
            x1={x(point.x)}
            x2={x(point.x)}
            y1={PAD_TOP}
            y2={HEIGHT - PAD_BOTTOM}
            className="stroke-muted-foreground"
            strokeWidth={1}
            strokeDasharray="3 3"
            strokeOpacity={0.45}
          />
          {/* 라벨은 첫 자리에만 붙인다, 연장은 마감 직전에 몰려 여러 번이면 글자가 서로 겹친다 */}
          {index === 0 && (
            <text
              x={x(point.x) - 4}
              y={HEIGHT - PAD_BOTTOM - 4}
              textAnchor="end"
              className="fill-muted-foreground text-[11px]"
            >
              연장
            </text>
          )}
        </g>
      ))}

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
          {/* 곡선은 첫 입찰에서 시작한다. 시작가가 어디였는지는 이 바닥선이 대신 말한다 */}
          {tick.value === 0 && startPriceLabelFits && (
            <text x={PAD_LEFT + 6} y={y(0) - 6} className="fill-muted-foreground text-[11px]">
              시작가
            </text>
          )}
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
            className="stroke-bid-mine"
            strokeWidth={1.5}
            strokeDasharray="6 4"
          />
          {/* 라벨은 왼쪽 끝이다. 오른쪽에 두면 내 값이 낙찰가에 붙었을 때 낙찰 라벨과 겹친다 */}
          <text
            x={PAD_LEFT + 6}
            y={y(shape.myLineY) - 6}
            className="fill-bid-mine text-[11px] font-medium"
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

      {/*
        점 하나가 입찰 하나다. 남의 입찰은 작은 빈 점으로 남긴다 — 지워 버리면 몇십 건이
        들어온 경매도 매끈한 선 하나가 되어 마감 직전에 몰린 밀도가 사라진다.
        색은 누구인지만 말한다. 이겼는지는 낙찰 링이 따로 말한다
      */}
      {shape.points.map((point, index) => (
        <circle
          key={index}
          cx={x(point.x)}
          cy={y(point.y)}
          r={point.mine ? 5.5 : 2.6}
          strokeWidth={point.mine ? 2 : 1.5}
          strokeOpacity={point.mine ? 1 : 0.8}
          className={point.mine ? 'fill-bid-mine stroke-bid-mine' : 'fill-card stroke-price-up'}
        />
      ))}

      {/*
        낙찰 자리는 점의 색을 빼앗지 않고 링을 덧씌운다. 색이 "누구"와 "낙찰"을 함께 지면
        내가 낙찰받은 순간 두 뜻이 한 점에서 부딪혀 어느 점이 내 것인지 읽히지 않는다.
        링 아래 카드색 테를 깔아 마감 직전에 붙은 점들과 갈라 놓는다
      */}
      <g>
        <circle
          cx={x(last.x)}
          cy={y(last.y)}
          r={9.5}
          fill="none"
          className="stroke-card"
          strokeWidth={4}
        />
        <circle
          cx={x(last.x)}
          cy={y(last.y)}
          r={5.5}
          className={mineWon ? 'fill-bid-mine stroke-bid-mine' : 'fill-price-up stroke-price-up'}
        />
        <circle
          cx={x(last.x)}
          cy={y(last.y)}
          r={9.5}
          fill="none"
          className="stroke-price-up"
          strokeWidth={1.75}
        />
        <text
          x={labelFitsCentered ? x(last.x) : WIDTH - PAD_RIGHT}
          y={y(last.y) - 16}
          textAnchor={labelFitsCentered ? 'middle' : 'end'}
          className="fill-price-up text-[11px] font-semibold"
        >
          {winnerLabel}
        </text>
      </g>

      {/*
        가로축은 첫 입찰에서 마감까지다, 가운데 눈금이 있어야 오른쪽 여백이 남은 시간으로 읽힌다.
        왼쪽에 "첫 입찰"을 적어야 그 시각이 방이 열린 시각으로 오독되지 않는다
      */}
      {[
        { at: shape.originAt, x: 0, anchor: 'start' as const, tag: '첫 입찰 ' },
        { at: middleOf(shape.originAt, endAt), x: 0.5, anchor: 'middle' as const, tag: '' },
        { at: endAt, x: 1, anchor: 'end' as const, tag: '' },
      ].map((tick) => (
        <text
          key={tick.x}
          x={x(tick.x)}
          y={HEIGHT - 8}
          textAnchor={tick.anchor}
          className="fill-muted-foreground text-[11px]"
        >
          {tick.tag}
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
