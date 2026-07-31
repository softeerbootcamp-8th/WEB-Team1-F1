import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Minus, Plus } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { formatKRW, formatManwon } from '@/lib/format'
import { getErrorMessage } from '@/lib/axios'
import { useAuth } from '@/features/auth/auth-context'

interface BidPanelProps {
  currentPrice: number
  increment: number
  nextMin: number
  disabled?: boolean
  onBid: (amount: number) => Promise<void>
}

/**
 * 입찰 패널. 로그인한 회원이면 누구나(개인·딜러 모두) 입찰 가능.
 * 금액은 직접 입력하지 않고 호가 단위만큼 -/+로만 조정한다 — 단위에 안 맞는 금액을 낼 수 없다.
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
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 현재가·호가 단위가 바뀌면(다른 입찰이 성립하거나 구간이 넘어가면) 쌓아둔 +/- 조정값은
  // 새 단위에 안 맞을 수 있어 그대로 두지 않고 최소 입찰가로 되돌린다.
  useEffect(() => {
    setAmount(nextMin)
  }, [nextMin, increment])

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

  const submit = async () => {
    setIsSubmitting(true)
    try {
      await onBid(amount)
      toast.success('입찰 완료', { description: formatKRW(amount) })
      setAmount(amount + increment)
    } catch (error) {
      toast.error(getErrorMessage(error, '입찰에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
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

      <div className="flex items-center overflow-hidden rounded-md border">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-11 rounded-none"
          disabled={disabled || amount <= nextMin}
          onClick={() => setAmount((prev) => Math.max(nextMin, prev - increment))}
          aria-label="입찰가 낮추기"
        >
          <Minus className="size-4" />
        </Button>
        <div className="tabular border-x flex-1 py-2.5 text-center text-sm font-medium">
          {formatKRW(increment)}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-11 rounded-none"
          disabled={disabled}
          onClick={() => setAmount((prev) => prev + increment)}
          aria-label="입찰가 높이기"
        >
          <Plus className="size-4" />
        </Button>
      </div>

      <div className="tabular mt-3 rounded-md border py-3 text-center text-xl font-semibold">
        {formatKRW(amount)}
      </div>

      <p className="text-muted-foreground mt-2 text-xs">
        현재가 {formatKRW(currentPrice)} · 최소 입찰가{' '}
        <span className="text-foreground tabular font-medium">
          {formatKRW(nextMin)}
        </span>
      </p>

      <Button
        type="button"
        size="lg"
        className="mt-4 w-full"
        disabled={disabled || isSubmitting || amount < nextMin}
        onClick={submit}
      >
        입찰
      </Button>
    </div>
  )
}
