import { Pencil, Trash2 } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { canDeleteAuction, canEditAuction } from '@/lib/auction'
import type { AuctionBadgeStatus } from '@/types/domain'

interface MyAuctionActionsProps {
  /**
   * 지금 시각으로 판정한 뱃지 단계(badgeStatusAt). 서버가 준 phase 를 쓰지 않는 이유는
   * 카드가 뱃지를 시각으로 그리기 때문이다. 두 판정이 갈리면 한 카드 안에서
   * "입장 가능" 뱃지 옆에 수정 버튼이 남고, 눌러도 서버가 거절한다.
   */
  status: AuctionBadgeStatus
  onEdit: () => void
  onDelete: () => void
}

/**
 * 나의 경매 카드에 붙는 수정·삭제.
 * 지금 할 수 없는 동작은 버튼 자체를 내리고, 이유를 한 줄로 남긴다.
 */
export function MyAuctionActions({ status, onEdit, onDelete }: MyAuctionActionsProps) {
  const editable = canEditAuction(status)
  const deletable = canDeleteAuction(status)

  if (!editable && !deletable) {
    // 방이 열린 카드는 뱃지가 "입장 가능"으로 갈리지만, 그 뱃지는 입장 여부를 말할 뿐
    // 수정이 막힌 이유까지 설명하지는 않는다. 버튼이 사라진 까닭은 따로 밝힌다.
    const message =
      status === 'WAITING'
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
