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

interface PriceCurveProps {
  shape: CurveShape
  /** 곡선 아래에 적을 시각 눈금, 시작과 끝 둘 */
  startAt: string
  endAt: string
  myAmount: number | null
}

/**
 * 가격이 오른 과정을 그리는 선.
 *
 * 좌표 계산은 curveShapeOf 가 이미 끝냈다. 여기서 하는 일은 0에서 1 사이의 값을 viewBox 안의
 * 자리로 옮기는 것뿐이라, 눈금이 어떻게 정해졌는지는 이 파일이 몰라도 된다.
 */
export function PriceCurve({ shape, startAt, endAt, myAmount }: PriceCurveProps) {
  const x = (value: number) => PAD_LEFT + value * (WIDTH - PAD_LEFT - PAD_RIGHT)
  // y 는 0이 바닥이라 위아래를 뒤집어 화면 좌표로 옮긴다
  const y = (value: number) => HEIGHT - PAD_BOTTOM - value * (HEIGHT - PAD_TOP - PAD_BOTTOM)

  const line = shape.points.map((point) => `${x(point.x)},${y(point.y)}`).join(' ')
  const last = shape.points[shape.points.length - 1]

  return (
    <svg
      viewBox={`0 0 ${WIDTH} ${HEIGHT}`}
      className="w-full"
      role="img"
      aria-label={`시작가 ${formatManwon(shape.minAmount)}에서 ${formatManwon(shape.maxAmount)}까지 오른 과정`}
    >
      {/* 위아래 눈금선과 금액 */}
      {[
        { value: 1, amount: shape.maxAmount },
        { value: 0, amount: shape.minAmount },
      ].map((tick) => (
        <g key={tick.value}>
          <line
            x1={PAD_LEFT}
            x2={WIDTH - PAD_RIGHT}
            y1={y(tick.value)}
            y2={y(tick.value)}
            className="stroke-border"
            strokeWidth={1}
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
      {shape.myLineY !== null && myAmount !== null && (
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
          <text
            x={WIDTH - PAD_RIGHT}
            y={y(shape.myLineY) - 6}
            textAnchor="end"
            className="fill-destructive text-[11px] font-medium"
          >
            내 입찰 {formatManwon(myAmount)}
          </text>
        </g>
      )}

      <polyline
        points={line}
        fill="none"
        className="stroke-price-up"
        strokeWidth={2.5}
        strokeLinejoin="round"
        strokeLinecap="round"
      />

      {shape.points.map((point, index) => (
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
          x={x(last.x)}
          y={y(last.y) - 12}
          textAnchor="middle"
          className="fill-price-up text-[11px] font-semibold"
        >
          낙찰
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
