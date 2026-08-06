import { Pencil, Trash2 } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { canDeleteAuction, canEditAuction } from '@/lib/auction'
import type { AuctionListCard } from '@/features/auctions/types'

interface MyAuctionActionsProps {
  auction: AuctionListCard
  onEdit: () => void
  onDelete: () => void
}

/**
 * 나의 경매 카드에 붙는 수정·삭제.
 * 지금 할 수 없는 동작은 버튼 자체를 내리고, 이유를 한 줄로 남긴다.
 */
export function MyAuctionActions({ auction, onEdit, onDelete }: MyAuctionActionsProps) {
  const editable = canEditAuction(auction.phase)
  const deletable = canDeleteAuction(auction.phase)

  if (!editable && !deletable) {
    // 방이 열렸는데 아직 시작 전이면 뱃지는 그대로 "예정"이다. 옆의 예정 카드에는 수정 버튼이
    // 있는데 이 카드만 없는 상태라, 사라진 이유를 밝히지 않으면 같은 상태로 보인다.
    const message =
      auction.phase === 'WAITING'
        ? '경매방이 열려 수정할 수 없습니다.'
        : '경매가 끝나면 삭제할 수 있습니다.'

    return <p className="text-muted-foreground text-xs">{message}</p>
  }

  return (
    <div className="flex gap-2">
      {editable && (
        <Button variant="outline" size="sm" className="flex-1" onClick={onEdit}>
          <Pencil className="size-3.5" />
          수정
        </Button>
      )}
      {deletable && (
        <Button
          variant="outline"
          size="sm"
          className="text-destructive hover:text-destructive flex-1"
          onClick={onDelete}
        >
          <Trash2 className="size-3.5" />
          삭제
        </Button>
      )}
    </div>
  )
}
