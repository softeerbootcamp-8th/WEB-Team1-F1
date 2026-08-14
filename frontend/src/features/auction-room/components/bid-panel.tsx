import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Minus, Plus } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { formatKRW, formatManwon } from '@/lib/format'
import { getErrorCode, getErrorMessage } from '@/lib/axios'
import { useAuth } from '@/features/auth/auth-context'
import { useCountdown } from '@/hooks/use-countdown'

import { acceptsBidAt } from '../deadline'
import { bidBlockOf, type BidBlock } from '../bid-eligibility'

interface BidPanelProps {
  currentPrice: number
  // 구간표를 받기 전에는 상승가와 최소 입찰가를 정할 수 없다
  increment: number | null
  nextMin: number | null
  /** 조회한 사람이 이 차를 내놓은 사람인지, 서버가 판정해 준다 */
  sellerIsMine: boolean
  /** 상자 바닥 왼쪽에 서는 도움말, 갈래를 타지 않게 밖에서 받는다 */
  help?: ReactNode
  /** 서버가 정한 마감 시각, 마감 임박 입찰로 밀리면 새 값이 온다 */
  endAt: string
  /** 서버 시각 - 브라우저 시계 */
  clockOffset: number
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
  help,
  sellerIsMine,
  endAt,
  clockOffset,
  onBid,
}: BidPanelProps) {
  const { user, isAuthenticated } = useAuth()

  // 1초마다 다시 그려 마감을 넘긴 순간을 잡는다, 마지막 1초는 제출 직전 재확인이 막는다
  // 조기 반환 앞에 둔다, 뒤에 두면 로그인 상태가 바뀔 때 훅 수가 달라진다
  useCountdown(endAt, 1000, clockOffset)

  // 폼과 바닥 줄이 같은 판정을 봐야 해서 갈래 밖에서 한 번 구한다
  const block = user ? bidBlockOf(user.role, sellerIsMine) : null

  // 상자는 갈래마다 다시 그리지 않고 여기서 한 번 그린다. 기준가와 도움말이 상자 바닥에
  // 붙는데, 갈래 안쪽에 두면 로그인 전이나 마감 뒤에는 그 둘이 사라진다
  return (
    <div className="rounded-xl border p-5">
      {body()}

      {/* 로그인 전과 마감 뒤에는 남긴다, 로그인하거나 다음 경매에서 쓸 값이다.
          입찰이 막힌 사람에게는 열릴 일이 없어 호가 단위와 최소 입찰가가 읽을 이유 없는 값이 된다.
          최소 입찰가를 모르는 동안에도 감춘다, 도움말이 여는 구간표도 같은 출처라 함께 비어 있다 */}
      {block === null && nextMin !== null && (
        <div className="text-muted-foreground mt-3 flex items-center justify-between gap-3 text-xs">
          {help}
          <p>
            현재가 {formatKRW(currentPrice)} · 최소 입찰가{' '}
            <span className="text-foreground tabular font-medium">{formatKRW(nextMin)}</span>
          </p>
        </div>
      )}
    </div>
  )

  function body() {
    if (!isAuthenticated || !user) {
      return (
        <div className="text-center">
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
    if (block !== null) {
      return <BidBlocked {...BLOCK_NOTICE[block]} />
    }

    // 마감 뒤에는 서버가 어떤 입찰도 받지 않는다, 화면이 폼을 열어 두면 눌러서 실패를 받게 된다
    if (!acceptsBidAt(endAt, Date.now() + clockOffset)) {
      return (
        <BidBlocked title="입찰이 마감됐습니다" description="곧 결과 화면으로 넘어갑니다." />
      )
    }

    // 호가 단위를 모르면 얼마를 낼 수 있는지 안내할 수 없다. 0으로 대체하면
    // 올리지 않아도 되는 입찰을 안내하게 되고 그 입찰은 서버가 거부한다.
    if (increment === null || nextMin === null) {
      return (
        <p className="text-muted-foreground text-center text-sm">
          호가 단위를 불러오는 중입니다.
        </p>
      )
    }

    return (
      <BidForm
        currentPrice={currentPrice}
        increment={increment}
        nextMin={nextMin}
        endAt={endAt}
        clockOffset={clockOffset}
        onBid={onBid}
      />
    )
  }
}

function BidBlocked({ title, description }: { title: string; description: string }) {
  return (
    <div className="text-center">
      <p className="text-sm font-semibold">{title}</p>
      <p className="text-muted-foreground mt-1 text-sm">{description}</p>
    </div>
  )
}

// 금액이 성립하지 않아 떨어진 입찰, 다른 실패와 달리 무엇을 고쳐야 하는지 화면이 말해줄 수 있다
const AMOUNT_REJECTIONS = new Set(['BID_AMOUNT_TOO_LOW', 'BID_AMOUNT_NOT_ALIGNED'])

// 값이 확정된 뒤에만 마운트된다, 덕분에 훅이 nullable 을 다루지 않는다
function BidForm({
  currentPrice,
  increment,
  nextMin,
  endAt,
  clockOffset,
  onBid,
}: {
  currentPrice: number
  increment: number
  nextMin: number
  endAt: string
  clockOffset: number
  onBid: (amount: number) => Promise<void>
}) {
  const [amount, setAmount] = useState(nextMin)
  const [isSubmitting, setIsSubmitting] = useState(false)
  // 금액 때문에 떨어진 직전 입찰의 서버 문구, 안 떨어졌으면 null
  const [rejection, setRejection] = useState<string | null>(null)

  // 최소 입찰가가 올라도 amount 를 따라 올리지 않는다, 고른 금액이 손가락 아래에서 바뀌면
  // 의도한 적 없는 금액이 나간다. 성립 여부는 보내서 서버에게 듣는다
  // 정렬도 보는 이유는 서버가 현재가 기준 배수까지 보기 때문이다
  const isOutdated = amount < nextMin || (amount - nextMin) % increment !== 0

  const changeAmount = (next: number) => {
    setRejection(null)
    setAmount(next)
  }

  const submit = async () => {
    // 잠금은 1초마다 다시 그려 걸리므로 마지막 1초가 남는다, 보내기 직전에 같은 규칙으로 한 번 더 본다
    if (!acceptsBidAt(endAt, Date.now() + clockOffset)) return

    setIsSubmitting(true)
    try {
      await onBid(amount)
      toast.success('입찰 완료', { description: formatKRW(amount) })
      changeAmount(amount + increment)
    } catch (error) {
      const message = getErrorMessage(error, '입찰에 실패했습니다')

      // 금액 문제는 폼 안에서 고칠 수 있다, 사라지는 토스트 대신 폼에 남겨 고칠 값 옆에 둔다
      if (AMOUNT_REJECTIONS.has(getErrorCode(error) ?? '')) {
        setRejection(message)
      } else {
        toast.error(message)
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div>
      <div className="mb-3 text-sm font-semibold">입찰하기</div>

      <div className="flex items-center overflow-hidden rounded-md border">
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-11 rounded-none"
          disabled={amount <= nextMin}
          onClick={() => changeAmount(Math.max(nextMin, amount - increment))}
          aria-label="입찰가 낮추기"
        >
          <Minus className="size-4" />
        </Button>
        {/* 한 번 누를 때 오르내리는 폭을 버튼 사이에 둔다, 고른 금액은 입찰 버튼이 들고 있다 */}
        <div className="tabular border-x flex-1 py-2.5 text-center text-sm font-medium">
          {formatManwon(increment)}원
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          className="h-11 rounded-none"
          onClick={() => changeAmount(amount + increment)}
          aria-label="입찰가 높이기"
        >
          <Plus className="size-4" />
        </Button>
      </div>

      {/* 거절 사유를 남긴다. 새 현재가가 도착하기 전이라면 서버 문구를 그대로 보여주고,
          도착한 뒤에는 그 값으로 무엇이 막았는지와 얼마부터 되는지를 말한다 */}
      {rejection !== null && (
        <p className="text-destructive mt-3 text-xs">
          {isOutdated
            ? `현재가가 이미 ${formatKRW(currentPrice)}입니다, 최소 입찰가는 ${formatKRW(nextMin)}입니다.`
            : rejection}
        </p>
      )}

      {/* 누르면 얼마가 나가는지를 버튼이 들고 있다, 확인과 실행이 한자리다 */}
      <Button
        type="button"
        size="lg"
        className="tabular mt-3 w-full"
        disabled={isSubmitting}
        onClick={submit}
      >
        {formatKRW(amount)} 입찰
      </Button>

      {/* 거절당한 뒤에만 낸다. 새 금액을 미리 채워 주지 않고, 오른 금액을 보고 누른 것이 되어야 나간다 */}
      {rejection !== null && isOutdated && (
        <Button
          type="button"
          size="lg"
          variant="outline"
          className="tabular mt-2 w-full"
          onClick={() => changeAmount(nextMin)}
        >
          {formatKRW(nextMin)}으로 맞추기
        </Button>
      )}
    </div>
  )
}
