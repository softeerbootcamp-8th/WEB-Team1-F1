import { toast } from 'sonner'

import { Card } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { CarThumb } from '@/components/common/car-thumb'
import { cn } from '@/lib/utils'
import { formatKRW, formatRelativeTime } from '@/lib/format'
import {
  DEAL_FLOW,
  DEAL_STATUS_META,
  dealProgress,
} from '@/lib/auction'
import type { Deal } from '@/types/domain'

interface DealCardProps {
  deal: Deal
  onAction: (dealId: number, label: string) => void
}

/** 거래 카드 — 파이프라인 진행률 + (이 거래에서의) 내 역할별 액션. */
export function DealCard({ deal, onAction }: DealCardProps) {
  const meta = DEAL_STATUS_META[deal.status]
  const progress = dealProgress(deal.status)
  const cancelled = deal.status === 'CANCELLED'

  return (
    <Card className="gap-4 p-5">
      <div className="flex gap-4">
        <div className="bg-muted size-20 shrink-0 overflow-hidden rounded-lg border">
          <CarThumb src={deal.thumbnailUrl} alt={deal.carName} />
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <h3 className="truncate font-semibold">{deal.carName}</h3>
            <Badge variant={cancelled ? 'destructive' : deal.status === 'COMPLETED' ? 'success' : 'secondary'}>
              {meta.label}
            </Badge>
          </div>
          <p className="tabular text-price-up mt-1 text-lg font-semibold">
            {formatKRW(deal.finalPrice)}
          </p>
          <p className="text-muted-foreground text-xs">
            {deal.myRole === 'BUYER' ? '판매자' : '구매자'} {deal.counterpartNickname} ·{' '}
            {formatRelativeTime(deal.updatedAt)}
          </p>
        </div>
      </div>

      {!cancelled && (
        <div className="space-y-2">
          <Progress
            value={progress}
            indicatorClassName={deal.status === 'COMPLETED' ? 'bg-price-up' : undefined}
          />
          <ol className="text-muted-foreground flex justify-between text-[11px]">
            {DEAL_FLOW.map((s) => (
              <li
                key={s}
                className={cn(
                  DEAL_FLOW.indexOf(s) <= DEAL_FLOW.indexOf(deal.status) &&
                    'text-foreground font-medium',
                )}
              >
                {DEAL_STATUS_META[s].label.replace(' 대기', '')}
              </li>
            ))}
          </ol>
        </div>
      )}

      <p className="text-muted-foreground border-t pt-3 text-sm">{meta.description}</p>

      <DealActions deal={deal} onAction={onAction} />
    </Card>
  )
}

/** 거래에서의 내 역할(판매자/구매자)에 따른 액션 버튼. */
function DealActions({ deal, onAction }: DealCardProps) {
  const act = (label: string) => {
    onAction(deal.id, label)
    toast.success(`${label} 처리되었습니다`)
  }

  // 이 거래의 판매자: 확정/철회, 탁송 입력
  if (deal.myRole === 'SELLER') {
    if (deal.status === 'PENDING_SELLER') {
      return (
        <div className="flex gap-2">
          <Button size="sm" className="flex-1" onClick={() => act('거래 확정')}>
            확정
          </Button>
          <Button size="sm" variant="outline" className="flex-1" onClick={() => act('거래 철회')}>
            철회
          </Button>
        </div>
      )
    }
    if (deal.status === 'CONFIRMED') {
      return (
        <Button size="sm" className="w-full" onClick={() => act('탁송 정보 입력')}>
          탁송 정보 입력
        </Button>
      )
    }
  }

  // 이 거래의 구매자: 배송 입력
  if (deal.myRole === 'BUYER' && deal.status === 'IN_TRANSIT') {
    return (
      <Button size="sm" className="w-full" onClick={() => act('배송 정보 입력')}>
        배송 정보 입력
      </Button>
    )
  }

  return null
}
