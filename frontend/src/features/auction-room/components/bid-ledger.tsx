import { ScrollArea } from '@/components/ui/scroll-area'
import { Badge } from '@/components/ui/badge'
import { EmptyState } from '@/components/common/empty-state'
import { cn } from '@/lib/utils'
import { formatClock, formatKRW } from '@/lib/format'
import { Gavel } from 'lucide-react'
import type { RecentBid } from '@/features/auction-room/types'

interface BidLedgerProps {
  bids: RecentBid[]
  totalBidCount: number
}

/**
 * 호가창. 최신순, 최고가(맨 위) 강조. 이름은 백엔드가 이미 마스킹해서 내려준다.
 * 실제로는 커서 페이지네이션으로 과거 호가를 더 불러온다.
 */
export function BidLedger({ bids, totalBidCount }: BidLedgerProps) {
  const visibleBids = bids.slice(0, 20)

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between px-1 pb-3">
        <h3 className="text-sm font-semibold">호가창</h3>
        <span className="text-muted-foreground text-xs">
          총 <span className="tabular">{totalBidCount}</span>건 입찰
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
            {visibleBids.map((bid, i) => {
              const isTop = i === 0
              return (
                <li
                  key={`${bid.bidAt}-${bid.amount}-${i}`}
                  className={cn(
                    'flex items-center justify-between gap-3 px-4 py-2.5 text-sm',
                    bid.mine ? 'bg-muted' : isTop && 'bg-price-up/8',
                  )}
                >
                  <div className="flex items-center gap-2">
                    <Badge
                      variant="outline"
                      className="h-4 px-1 text-[10px] font-normal"
                    >
                      {bid.role === 'DEALER' ? '딜러' : bid.role === 'GENERAL' ? '일반' : '평가사'}
                    </Badge>
                    <span className="font-medium">{bid.name}</span>
                    {bid.mine && (
                      <span className="text-muted-foreground text-xs">(나)</span>
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
                      {formatClock(bid.bidAt)}
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
