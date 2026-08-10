import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { AUCTION_BADGE_META } from '@/lib/auction'
import type { AuctionBadgeStatus } from '@/types/domain'

interface StatusBadgeProps {
  status: AuctionBadgeStatus
  className?: string
}

/** 경매 상태 뱃지. 진행중이면 라이브 점(●)이 점멸한다. */
export function StatusBadge({ status, className }: StatusBadgeProps) {
  const meta = AUCTION_BADGE_META[status]
  return (
    <Badge variant={meta.variant} className={cn('gap-1.5', className)}>
      {status === 'LIVE' && (
        <span
          className="bg-status-live size-1.5 rounded-full"
          style={{ animation: 'var(--animate-live-pulse)' }}
          aria-hidden
        />
      )}
      {meta.label}
    </Badge>
  )
}
