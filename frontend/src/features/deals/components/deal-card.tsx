import { ChevronRight } from 'lucide-react'
import { Link } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Card } from '@/components/ui/card'
import { CarThumb } from '@/components/common/car-thumb'
import { formatKRW, formatRelativeTime } from '@/lib/format'
import { DEAL_STATUS_META } from '../types'
import type { DealCard as DealCardData } from '../types'

/**
 * 거래 목록 카드. 카드 전체가 상세로 가는 링크다 — 이 화면에서 할 수 있는 일이 그것뿐이라
 * 버튼을 따로 두면 누를 곳이 두 군데가 된다.
 */
export function DealCard({ deal }: { deal: DealCardData }) {
  const cancelled = deal.status === 'CANCELLED'
  const meta = DEAL_STATUS_META[deal.status]

  return (
    <Card className="p-0 transition-colors hover:border-foreground/20">
      <Link
        to={`/deals/${deal.dealId}`}
        className="flex items-center gap-4 p-5"
        aria-label={`${deal.model} 거래 상세`}
      >
        <div className="bg-muted size-20 shrink-0 overflow-hidden rounded-lg border">
          <CarThumb src={deal.thumbnailUrl ?? undefined} alt={deal.model} />
        </div>

        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <Badge variant={cancelled ? 'destructive' : deal.status === 'CONFIRMED' ? 'success' : 'secondary'}>
              {meta.label}
            </Badge>
            {/* 내 차례라는 표시가 목록에서 가장 먼저 보여야 하는 정보다 */}
            {deal.actionRequired && <Badge variant="warning">내 차례</Badge>}
          </div>

          <h3 className="mt-2 truncate font-semibold">{deal.model}</h3>
          <p className="tabular mt-1 font-semibold">{formatKRW(deal.finalPrice)}</p>
          <p className="text-muted-foreground mt-1 text-xs">
            {deal.mySide === 'BUYER' ? '판매자' : '구매자'} {deal.counterpartName} ·{' '}
            {formatRelativeTime(deal.statusChangedAt)}
          </p>
        </div>

        <ChevronRight className="text-muted-foreground size-5 shrink-0" aria-hidden />
      </Link>
    </Card>
  )
}
