import type { BidIncrementBand } from '@/features/auction-room/types'
import { bandForPrice } from '@/lib/auction'
import { formatManwon } from '@/lib/format'
import { cn } from '@/lib/utils'

interface BidIncrementHelpProps {
  bands: BidIncrementBand[]
  /** 지금 가격. 이 값이 속한 구간을 강조한다 */
  currentPrice: number
}

/**
 * 가격 구간별 최소 상승 금액 표.
 *
 * 구간표를 화면에 적어 두지 않고 경매방이 이미 받아 둔 값을 그대로 쓴다. 구간은 배포 없이
 * 바뀌는 정책값이라(DB `bid_increment_band`), 화면이 자기 표를 들면 도움말이 실제 판정과 다른
 * 금액을 안내하게 된다. 그래서 여는 시점에 따로 요청하지도 않는다.
 *
 * 현재 구간 판정은 입찰 패널이 상승가를 구할 때 쓰는 것과 같은 함수를 쓴다.
 */
export function BidIncrementHelp({
  bands,
  currentPrice,
}: BidIncrementHelpProps) {
  const sorted = [...bands].sort((a, b) => a.minPrice - b.minPrice)
  const current = bandForPrice(currentPrice, bands)

  return (
    <div className="space-y-3">
      <div>
        <p className="text-sm font-semibold">가격 구간별 최소 상승 금액</p>
        <p className="text-muted-foreground mt-1 text-xs">
          가격대가 올라가면 한 번에 올려야 하는 금액도 커집니다.
        </p>
      </div>

      {sorted.length === 0 ? (
        <p className="text-muted-foreground py-4 text-xs" role="status">
          기준을 불러오는 중입니다
        </p>
      ) : (
        <table className="w-full text-left text-xs">
          <caption className="sr-only">
            가격 구간별 최소 입찰 상승 금액, 현재 가격이 속한 구간 표시
          </caption>
          <thead className="text-muted-foreground">
            <tr>
              <th scope="col" className="py-1 font-medium">
                가격 구간
              </th>
              <th scope="col" className="py-1 text-right font-medium">
                최소 상승
              </th>
            </tr>
          </thead>
          <tbody>
            {sorted.map((band, index) => {
              const isCurrent = current?.minPrice === band.minPrice
              const next = sorted[index + 1]

              return (
                <tr
                  key={band.minPrice}
                  className={cn(
                    'border-t',
                    isCurrent && 'bg-accent font-semibold',
                  )}
                >
                  <td className="py-1.5 whitespace-nowrap">
                    {formatManwon(band.minPrice)}
                    {next ? ` ~ ${formatManwon(next.minPrice)}` : ' 이상'}
                    {/* 강조만 하면 낭독기에는 아무 차이가 없다 */}
                    {isCurrent && <span className="sr-only"> (현재 구간)</span>}
                  </td>
                  <td className="py-1.5 text-right whitespace-nowrap tabular-nums">
                    {formatManwon(band.increment)}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}
