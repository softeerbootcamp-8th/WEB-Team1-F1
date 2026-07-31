import { useEffect, useRef, useState } from 'react'
import { Countdown } from '@/components/common/countdown'
import { formatKRW } from '@/lib/format'
import { cn } from '@/lib/utils'

interface PriceBoardProps {
  currentPrice: number
  startPrice: number
  endAt: string
  extended: boolean
  /** 새 입찰마다 증가하는 키 — 값이 바뀌면 플래시 애니메이션 */
  flashKey: number
}

/**
 * 증권 시세판 스타일 현재가 보드.
 * 현재가를 큰 숫자로 강조하고, 새 입찰 시 플래시 + 상승 표시.
 */
export function PriceBoard({
  currentPrice,
  startPrice,
  endAt,
  extended,
  flashKey,
}: PriceBoardProps) {
  const [flash, setFlash] = useState(false)
  const prev = useRef(currentPrice)

  useEffect(() => {
    if (flashKey === 0) return
    setFlash(true)
    const t = window.setTimeout(() => setFlash(false), 600)
    prev.current = currentPrice
    return () => window.clearTimeout(t)
  }, [flashKey, currentPrice])

  return (
    <div className="dark bg-background text-foreground overflow-hidden rounded-xl border">
      <div className="grid md:grid-cols-[1fr_auto]">
        <div className="border-border border-b p-6 md:border-r md:border-b-0">
          <span className="text-muted-foreground text-xs font-medium tracking-widest uppercase">
            현재가
          </span>
          <div
            className={cn('-mx-2 mt-3 rounded-lg px-2 py-1 transition-colors')}
            style={flash ? { animation: 'var(--animate-bid-flash)' } : undefined}
          >
            <div className="flex items-baseline gap-2">
              <span className="tabular text-price-up text-5xl font-bold tracking-tight tabular-nums md:text-6xl">
                {currentPrice.toLocaleString('ko-KR')}
              </span>
              <span className="text-muted-foreground text-xl">원</span>
            </div>
          </div>
          <p className="text-muted-foreground mt-3 text-sm">
            시작가{' '}
            <span className="tabular text-foreground font-medium">
              {formatKRW(startPrice)}
            </span>
          </p>
        </div>

        <div className="flex min-w-52 flex-col justify-center p-6 md:text-right">
          <span className="text-muted-foreground text-xs font-medium tracking-widest uppercase">
            남은 시간
          </span>
          <Countdown
            targetIso={endAt}
            className="tabular mt-2 text-3xl font-semibold"
          />
          <p className="text-muted-foreground mt-2 min-h-5 text-xs">
            {extended ? '새 입찰이 반영되어 마감 시간이 연장됐습니다.' : '서버 마감 시각 기준'}
          </p>
        </div>
      </div>
    </div>
  )
}
