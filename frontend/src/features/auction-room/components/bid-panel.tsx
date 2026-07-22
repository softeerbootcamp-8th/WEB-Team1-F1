import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { formatKRW, formatManwon } from '@/lib/format'
import { useAuth } from '@/features/auth/auth-context'

interface BidPanelProps {

  currentPrice: number
  increment: number
  nextMin: number
  disabled?: boolean
  onBid: (amount: number) => { ok: boolean; reason?: 'TOO_LOW' }
}

/**
 * 입찰 패널. 로그인한 회원이면 누구나(개인·딜러 모두) 입찰 가능.
 * 호가 단위 기반 빠른 입찰 버튼 + 직접 입력.
 */
export function BidPanel({
  currentPrice,
  increment,
  nextMin,
  disabled,
  onBid,
}: BidPanelProps) {
  const { isAuthenticated } = useAuth()
  const [amount, setAmount] = useState(nextMin)

  // 현재가가 오르면 입력값 하한도 따라 올린다.
  useEffect(() => {
    setAmount((prev) => (prev < nextMin ? nextMin : prev))
  }, [nextMin])

  const quickSteps = [1, 2, 5]

  if (!isAuthenticated) {
    return (
      <div className="rounded-xl border p-5 text-center">
        <p className="text-muted-foreground mb-3 text-sm">
          입찰하려면 로그인이 필요합니다.
        </p>
        <Button asChild className="w-full">
          <Link to="/login">로그인하고 입찰하기</Link>
        </Button>
      </div>
    )
  }

  const submit = () => {
    const result = onBid(amount)
    if (result.ok) {
      toast.success('입찰 완료', { description: formatKRW(amount) })
      setAmount(amount + increment)
    } else if (result.reason === 'TOO_LOW') {
      toast.error('입찰가가 낮습니다', {
        description: `최소 ${formatKRW(nextMin)} 이상 입력하세요.`,
      })
    }
  }

  return (
    <div className="rounded-xl border p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-sm font-semibold">입찰하기</span>
        <span className="text-muted-foreground text-xs">
          호가 단위 <span className="tabular">{formatManwon(increment)}원</span>
        </span>
      </div>

      <div className="mb-3 flex gap-2">
        {quickSteps.map((mult) => {
          const value = nextMin + increment * (mult - 1)
          return (
            <Button
              key={mult}
              type="button"
              variant="outline"
              size="sm"
              className="flex-1"
              disabled={disabled}
              onClick={() => setAmount(value)}
            >
              +{formatManwon(increment * mult)}
            </Button>
          )
        })}
      </div>

      <label className="text-muted-foreground mb-1.5 block text-xs" htmlFor="bid-amount">
        입찰가 (원)
      </label>
      <div className="flex gap-2">
        <Input
          id="bid-amount"
          type="number"
          inputMode="numeric"
          step={increment}
          min={nextMin}
          value={amount}
          disabled={disabled}
          onChange={(e) => setAmount(Number(e.target.value))}
          className="tabular text-right text-base font-semibold"
        />
      </div>
      <p className="text-muted-foreground mt-1.5 text-xs">
        현재가 {formatKRW(currentPrice)} · 최소 입찰가{' '}
        <span className="text-foreground tabular font-medium">
          {formatKRW(nextMin)}
        </span>
      </p>

      <Button
        type="button"
        size="lg"
        className="mt-4 w-full"
        disabled={disabled || amount < nextMin}
        onClick={submit}
      >
        {formatKRW(amount)} 입찰
      </Button>
    </div>
  )
}
