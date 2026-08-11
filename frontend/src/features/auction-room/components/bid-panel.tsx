import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Minus, Plus } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { formatKRW, formatManwon } from '@/lib/format'
import { getErrorMessage } from '@/lib/axios'
import { useAuth } from '@/features/auth/auth-context'

import { bidBlockOf, type BidBlock } from '../bid-eligibility'

interface BidPanelProps {
  currentPrice: number
  // 구간표를 받기 전에는 상승가와 최소 입찰가를 정할 수 없다
  increment: number | null
  nextMin: number | null
  /** 조회한 사람이 이 차를 내놓은 사람인지, 서버가 판정해 준다 */
  sellerIsMine: boolean
  disabled?: boolean
  onBid: (amount: number) => Promise<void>
}

// 문구는 서버 것을 그대로 쓴다, 눌러서 토스트로 보던 말을 같은 자리에서 먼저 본다
const BLOCK_NOTICE: Record<BidBlock, { title: string; description: string }> = {
  EVALUATOR: { title: '평가사 계정입니다', description: '평가사는 입찰할 수 없습니다.' },
  SELLER: {
    title: '내가 내놓은 차량입니다',
    description: '판매자는 자기 차량에 입찰할 수 없습니다.',
  },
}

/**
 * 입찰 패널. 로그인한 개인·딜러 회원이 입찰한다.
 * 판매자는 자기 차량에, 평가사는 어느 차량에도 입찰할 수 없어 폼 대신 안내를 본다.
 * 금액은 직접 입력하지 않고 호가 단위만큼 -/+로만 조정한다 — 단위에 안 맞는 금액을 낼 수 없다.
 */
export function BidPanel({
  currentPrice,
  increment,
  nextMin,
  sellerIsMine,
  disabled,
  onBid,
}: BidPanelProps) {
  const { user, isAuthenticated } = useAuth()

  if (!isAuthenticated || !user) {
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

  // 이 둘은 기다린다고 열리지 않는다. 잠긴 폼을 남기면 절대 입찰하지 않을 사람에게
  // 호가 단위와 최소 입찰가를 계속 안내하게 된다
  const block = bidBlockOf(user.role, sellerIsMine)
  if (block !== null) {
    return <BidBlocked {...BLOCK_NOTICE[block]} />
  }

  // 호가 단위를 모르면 얼마를 낼 수 있는지 안내할 수 없다. 0으로 대체하면
  // 올리지 않아도 되는 입찰을 안내하게 되고 그 입찰은 서버가 거부한다.
  if (increment === null || nextMin === null) {
    return (
      <div className="rounded-xl border p-5 text-center">
        <p className="text-muted-foreground text-sm">
          호가 단위를 불러오는 중입니다.
        </p>
      </div>
    )
  }

  return (
    <BidForm
      currentPrice={currentPrice}
      increment={increment}
      nextMin={nextMin}
      disabled={disabled}
      onBid={onBid}
    />
  )
}

// 입찰 폼과 같은 상자를 쓴다, 자리도 테두리도 그대로라 이 사람만 레이아웃이 달라지지 않는다
function BidBlocked({ title, description }: { title: string; description: string }) {
  return (
    <div className="rounded-xl border p-5 text-center">
      <p className="text-sm font-semibold">{title}</p>
      <p className="text-muted-foreground mt-1 text-sm">{description}</p>
    </div>
  )
}

// 값이 확정된 뒤에만 마운트된다, 덕분에 훅이 nullable 을 다루지 않는다
function BidForm({
  currentPrice,
  increment,
  nextMin,
  disabled,
  onBid,
}: {
  currentPrice: number
  increment: number
  nextMin: number
  disabled?: boolean
  onBid: (amount: number) => Promise<void>
}) {
  const [amount, setAmount] = useState(nextMin)
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 현재가·호가 단위가 바뀌면(다른 입찰이 성립하거나 구간이 넘어가면) 쌓아둔 +/- 조정값은
  // 새 단위에 안 맞을 수 있어 그대로 두지 않고 최소 입찰가로 되돌린다.
  useEffect(() => {
    setAmount(nextMin)
  }, [nextMin, increment])

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
