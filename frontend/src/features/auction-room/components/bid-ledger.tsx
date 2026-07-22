import { ScrollArea } from '@/components/ui/scroll-area'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/common/empty-state'
import { cn } from '@/lib/utils'
import { formatClock, formatKRW } from '@/lib/format'
import { Gavel } from 'lucide-react'
import type { Bid } from '@/types/domain'

interface BidLedgerProps {
  bids: Bid[]
}

/**
 * 호가창. 최신순, 닉네임 마스킹, 최고가(맨 위) 강조.
 * 실제로는 커서 페이지네이션으로 과거 호가를 더 불러온다.
 */
export function BidLedger({ bids }: BidLedgerProps) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between px-1 pb-3">
        <h3 className="text-sm font-semibold">호가창</h3>
        <span className="text-muted-foreground text-xs">
          총 <span className="tabular">{bids.length}</span>건
        </span>
      </div>

      {bids.length === 0 ? (
        <EmptyState
          icon={Gavel}
          title="아직 입찰이 없습니다"
          description="첫 입찰의 주인공이 되어보세요."
          className="flex-1"
        />
      ) : (
        <ScrollArea className="h-[420px] rounded-lg border">
          <ul className="divide-border divide-y">
            {bids.map((bid, i) => {
              const isTop = i === 0
              return (
                <li
                  key={bid.id}
                  className={cn(
                    'flex items-center justify-between gap-3 px-4 py-2.5 text-sm',
                    isTop && 'bg-price-up/8',
                    bid.isMine && !isTop && 'bg-accent/50',
                  )}
                >
                  <div className="flex items-center gap-2">
                    <span className="text-muted-foreground tabular w-6 text-right text-xs">
                      {bids.length - i}
                    </span>
                    <span className="font-medium">{bid.bidderNickname}</span>
                    {bid.isMine && (
                      <Badge variant="outline" className="h-4 px-1 text-[10px]">
                        나
                      </Badge>
                    )}
                    {isTop && (
                      <Badge variant="success" className="h-4 px-1 text-[10px]">
                        최고가
                      </Badge>
                    )}
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={cn(
                        'tabular font-semibold',
                        isTop && 'text-price-up',
                      )}
                    >
                      {formatKRW(bid.amount)}
                    </span>
                    <span className="text-muted-foreground/70 tabular w-14 text-right text-xs">
                      {formatClock(bid.createdAt)}
                    </span>
                  </div>
                </li>
              )
            })}
          </ul>
        </ScrollArea>
      )}
    </div>
  )
}
