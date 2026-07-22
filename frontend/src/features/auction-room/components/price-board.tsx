import { useEffect, useRef, useState } from 'react'
import { TrendingUp } from 'lucide-react'

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
  onElapsed?: () => void
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
  onElapsed,
}: PriceBoardProps) {
  const [flash, setFlash] = useState(false)
  const prev = useRef(currentPrice)
  const delta = currentPrice - startPrice
  const deltaPct = startPrice > 0 ? (delta / startPrice) * 100 : 0

  useEffect(() => {
    if (flashKey === 0) return
    setFlash(true)
    const t = window.setTimeout(() => setFlash(false), 600)
    prev.current = currentPrice
    return () => window.clearTimeout(t)
  }, [flashKey, currentPrice])

  return (
    <div className="dark bg-background text-foreground rounded-xl border p-6">
      <div className="flex items-center justify-between">
        <span className="text-muted-foreground text-xs font-medium tracking-widest uppercase">
          현재가
        </span>
        <div className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">마감까지</span>
          <Countdown
            targetIso={endAt}
            className="text-lg font-semibold"
            onElapsed={onElapsed}
          />
        </div>
      </div>

      <div
        className={cn(
          '-mx-2 mt-3 rounded-lg px-2 py-1 transition-colors',
        )}
        style={flash ? { animation: 'var(--animate-bid-flash)' } : undefined}
      >
        <div className="flex items-baseline gap-2">
          <span className="tabular text-price-up text-5xl font-bold tracking-tight tabular-nums md:text-6xl">
            {currentPrice.toLocaleString('ko-KR')}
          </span>
          <span className="text-muted-foreground text-2xl">원</span>
        </div>
      </div>

      <div className="text-muted-foreground mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
        <span className="text-price-up inline-flex items-center gap-1 font-medium">
          <TrendingUp className="size-4" />+{formatKRW(delta)} ({deltaPct.toFixed(1)}%)
        </span>
        <span>
          시작가 <span className="tabular text-foreground">{formatKRW(startPrice)}</span>
        </span>
      </div>

      {extended && (
        <p
          role="alert"
          className="bg-closing-soon/15 text-closing-soon mt-4 rounded-md px-3 py-2 text-center text-sm font-medium"
        >
          ⏱ 마감 임박 입찰로 시간이 30초 연장되었습니다
        </p>
      )}
    </div>
  )
}
