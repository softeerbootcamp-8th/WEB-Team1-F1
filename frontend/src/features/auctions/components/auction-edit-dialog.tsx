import { useEffect, useState } from 'react'
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
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { updateAuction } from '@/features/auctions/api'
import type { AuctionListCard } from '@/features/auctions/types'
import { getErrorMessage } from '@/lib/axios'
import { MIN_START_LEAD_TIME_MS } from '@/lib/auction'
import { formatKRW } from '@/lib/format'

/**
 * datetime-local 입력이 쓰는 "YYYY-MM-DDTHH:mm" 문자열로. 서버가 받는 LocalDateTime도
 * 타임존이 없는 현지 시각이라 두 표기가 그대로 맞는다.
 * toISOString()은 UTC로 밀어버리므로 여기서는 쓰면 안 된다.
 */
// 시작가 입력 상한(만원 단위 6자리 = 999,999만원 ≈ 100억). 중고차 시세로는 닿을 일이 없고,
// 서버는 상한이 없어(@PositiveOrZero) 자릿수 실수를 걸러 줄 곳이 여기뿐이다.
const MAX_PRICE_DIGITS = 6

function toLocalInputValue(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

interface AuctionEditDialogProps {
  auction: AuctionListCard | null
  onOpenChange: (open: boolean) => void
  onUpdated: () => void
}

/** 경매글 수정. 시작가와 시작 시각만 바꿀 수 있고, 경매방이 열리기 전에만 통과한다. */
export function AuctionEditDialog({
  auction,
  onOpenChange,
  onUpdated,
}: AuctionEditDialogProps) {
  // 시작가는 만원 단위로만 받는다. 원 단위로 받아 검증하는 대신 입력 단위를 만원으로 두면
  // 1,343만 3,241원 같은 값이 애초에 만들어지지 않는다. 저장할 때만 원으로 환산한다.
  const [priceManwon, setPriceManwon] = useState('')
  const [startAt, setStartAt] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  // 다른 경매를 열 때마다 그 경매의 현재 값으로 초기화한다.
  useEffect(() => {
    if (!auction) return
    // 만원 미만이 섞인 기존 값은 버림 처리한다. 어차피 이 화면을 거치면 만원 단위가 된다.
    setPriceManwon(String(Math.floor(auction.startPrice / 10000)))
    // 응답의 startAt은 타임존 없는 현지 시각이라 분 단위까지 자르면 입력값 형식이 된다.
    setStartAt(auction.startAt.slice(0, 16))
  }, [auction])

  const minStartAt = toLocalInputValue(new Date(Date.now() + MIN_START_LEAD_TIME_MS))
  const priceInWon = Number(priceManwon || 0) * 10000
  const hasPrice = priceManwon !== ''

  // 입력의 min 속성은 폼 제출 경로에서만 걸린다. 여기는 버튼 onClick으로 보내므로 직접 막는다.
  // 두 값 모두 'YYYY-MM-DDTHH:mm' 고정 폭이라 문자열 비교가 곧 시간 비교다.
  const isStartAtValid = !!startAt && startAt >= minStartAt
  const canSubmit = !!auction && isStartAtValid && hasPrice && !isSubmitting

  const submit = async () => {
    if (!auction || !canSubmit) return

    setIsSubmitting(true)
    try {
      await updateAuction(auction.auctionId, {
        startPrice: priceInWon,
        startAt: `${startAt}:00`,
      })
      toast.success('경매글을 수정했습니다')
      onOpenChange(false)
      onUpdated()
    } catch (error) {
      toast.error(getErrorMessage(error, '경매글 수정에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    // 저장 중에는 닫지 않는다. 닫아도 요청은 계속 가서, 성공하면 취소한 줄 아는 사용자에게
    // "수정했습니다" 토스트가 뜬다. 바깥 클릭·Esc도 같은 경로라 여기서 함께 막힌다.
    <Dialog open={!!auction} onOpenChange={(open) => !isSubmitting && onOpenChange(open)}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>경매글 수정</DialogTitle>
          <DialogDescription>
            {auction?.model} · 경매방이 열리기 전까지만 수정할 수 있습니다.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-5 py-2">
          <div className="grid gap-2">
            <Label htmlFor="edit-start-price">시작가</Label>
            <div className="relative">
              <Input
                id="edit-start-price"
                // number가 아니라 text다. 스피너 화살표와 휠 조작을 없애고 천단위 콤마를 찍기 위해서다.
                type="text"
                inputMode="numeric"
                autoComplete="off"
                value={priceManwon === '' ? '' : Number(priceManwon).toLocaleString('ko-KR')}
                onChange={(event) =>
                  // 숫자만 남기고 앞자리 0을 정리한다. 자릿수를 막지 않으면 "1343만3241원"을
                  // 통째로 붙여넣었을 때 숫자만 이어붙어 1,343억짜리 경매가 만들어진다.
                  //
                  // 자르기가 0 제거보다 뒤에 온다. 순서를 바꾸면 "0000001"을 붙여넣었을 때
                  // 앞 6자리("000000")만 남고 그게 "0"으로 정리되어 사용자가 넣은 1이 사라진다.
                  setPriceManwon(
                    event.target.value
                      .replace(/\D/g, '')
                      .replace(/^0+(?=\d)/, '')
                      .slice(0, MAX_PRICE_DIGITS),
                  )
                }
                className="tabular pr-14"
              />
              <span className="text-muted-foreground pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm">
                만원
              </span>
            </div>
            <p className="text-muted-foreground text-xs">
              {hasPrice ? formatKRW(priceInWon) : '시작가를 만원 단위로 입력해 주세요'}
            </p>
          </div>

          <div className="grid gap-2">
            <Label htmlFor="edit-start-at">경매 시작 시각</Label>
            <Input
              id="edit-start-at"
              type="datetime-local"
              min={minStartAt}
              value={startAt}
              onChange={(event) => setStartAt(event.target.value)}
              aria-invalid={!!startAt && !isStartAtValid}
            />
            {/* 저장 버튼만 비활성화하면 왜 눌리지 않는지 알 수 없다. 이유를 여기서 밝힌다. */}
            {!!startAt && !isStartAtValid ? (
              <p className="text-destructive text-xs">
                지금부터 1시간 뒤 이후로 지정해 주세요.
              </p>
            ) : (
              <p className="text-muted-foreground text-xs">
                지금부터 1시간 뒤 이후로만 지정할 수 있습니다. 경매방은 시작 30분 전에 열립니다.
              </p>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button
            variant="outline"
            disabled={isSubmitting}
            onClick={() => onOpenChange(false)}
          >
            취소
          </Button>
          <Button onClick={submit} disabled={!canSubmit}>
            {isSubmitting ? '저장 중…' : '저장'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
