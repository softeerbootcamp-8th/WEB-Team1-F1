import { ListFilter, RotateCcw, X } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { AuctionFilterPanel } from '@/features/auctions/components/auction-filter-panel'
import { countActiveFilters, type AuctionVehicleFilter } from '@/features/auctions/filter'
import type { AuctionStatus } from '@/types/domain'

interface MobileAuctionFilterProps {
  value: AuctionVehicleFilter
  onChange: (next: AuctionVehicleFilter) => void
  status: AuctionStatus | null
  onStatusChange: (next: AuctionStatus | null) => void
  onReset: () => void
}

/**
 * 모바일 목록은 차량부터 보여 주고 조건은 필요할 때 연다. 전역 메뉴와 다른 버튼·위치·제목을
 * 써서, 이 패널의 변경이 현재 경매 목록에만 적용된다는 범위를 드러낸다.
 */
export function MobileAuctionFilter({
  value,
  onChange,
  status,
  onStatusChange,
  onReset,
}: MobileAuctionFilterProps) {
  const activeCount = countActiveFilters(value) + (status ? 1 : 0)

  return (
    <div className="mb-6 flex items-center gap-2 lg:hidden">
      <Dialog>
        <DialogTrigger asChild>
          <Button
            variant="outline"
            size="lg"
            className="min-w-0 flex-1 justify-between"
            aria-label={
              activeCount > 0 ? `필터 열기, ${activeCount}개 적용` : '필터 열기, 조건 없음'
            }
          >
            <span className="flex items-center gap-2">
              <ListFilter className="size-4" />
              필터
            </span>
            <span className="text-muted-foreground text-sm" aria-live="polite">
              {activeCount > 0 ? `${activeCount}개 적용` : '조건 없음'}
            </span>
          </Button>
        </DialogTrigger>

        <DialogContent
          showCloseButton={false}
          className="top-auto bottom-0 left-0 flex h-[90dvh] w-full max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-t-2xl border-x-0 border-b-0 p-0 data-[state=closed]:slide-out-to-bottom data-[state=closed]:zoom-out-100 data-[state=open]:slide-in-from-bottom data-[state=open]:zoom-in-100 sm:max-w-none"
        >
          <DialogHeader className="flex-row items-center justify-between gap-4 border-b px-5 py-4 text-left">
            <div className="min-w-0">
              <DialogTitle className="text-xl">경매 필터</DialogTitle>
              <DialogDescription className="mt-1">
                {activeCount > 0 ? `${activeCount}개 조건이 적용 중입니다.` : '원하는 차량 조건을 선택하세요.'}
              </DialogDescription>
            </div>
            <div className="flex shrink-0 items-center gap-1">
              {activeCount > 0 && (
                <Button variant="ghost" size="sm" onClick={onReset}>
                  <RotateCcw className="size-4" />
                  초기화
                </Button>
              )}
              <DialogClose asChild>
                <Button variant="ghost" size="icon" aria-label="필터 닫기">
                  <X className="size-5" />
                </Button>
              </DialogClose>
            </div>
          </DialogHeader>

          <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain">
            <AuctionFilterPanel
              value={value}
              onChange={onChange}
              status={status}
              onStatusChange={onStatusChange}
              onReset={onReset}
              showHeader={false}
              className="rounded-none border-0 px-5 py-0 pb-6"
            />
          </div>

          <div className="border-t bg-background p-4">
            <DialogClose asChild>
              <Button size="lg" className="w-full">
                목록 보기
              </Button>
            </DialogClose>
          </div>
        </DialogContent>
      </Dialog>

      {activeCount > 0 && (
        <Button variant="ghost" size="lg" onClick={onReset} aria-label="필터 전체 초기화">
          <RotateCcw className="size-4" />
          <span className="hidden sm:inline">초기화</span>
        </Button>
      )}
    </div>
  )
}
