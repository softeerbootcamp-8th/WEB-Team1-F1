import { useState } from 'react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { deleteAuction } from '@/features/auctions/api'
import type { AuctionListCard } from '@/features/auctions/types'
import { getErrorMessage } from '@/lib/axios'

interface AuctionDeleteDialogProps {
  auction: AuctionListCard | null
  onOpenChange: (open: boolean) => void
  onDeleted: () => void
}

/** 경매글 삭제 확인. 끝난 경매만 지울 수 있고, 목록·상세에서 사라진다. */
export function AuctionDeleteDialog({
  auction,
  onOpenChange,
  onDeleted,
}: AuctionDeleteDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false)

  const submit = async () => {
    if (!auction) return

    setIsSubmitting(true)
    try {
      await deleteAuction(auction.auctionId)
      toast.success('경매글을 삭제했습니다')
      onOpenChange(false)
      onDeleted()
    } catch (error) {
      toast.error(getErrorMessage(error, '경매글 삭제에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Dialog open={!!auction} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>경매글을 삭제할까요?</DialogTitle>
          <DialogDescription>
            {auction?.model} 경매글이 목록에서 사라집니다. 되돌릴 수 없습니다.
          </DialogDescription>
        </DialogHeader>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button variant="destructive" onClick={submit} disabled={isSubmitting}>
            {isSubmitting ? '삭제 중…' : '삭제'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
