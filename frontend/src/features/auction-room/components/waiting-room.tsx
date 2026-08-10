import { Eye, FileText, Lock } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { Countdown } from '@/components/common/countdown'
import { formatClock, formatKRW, formatMileage } from '@/lib/format'
import { FUEL_TYPE_LABEL } from '@/features/quote/types'
import { CarPhotos } from '@/features/auction-room/components/car-detail'
import type { AuctionRoomView } from '@/features/auction-room/types'

interface WaitingRoomProps {
  room: AuctionRoomView
  /** 서버 시각 - 브라우저 시계 */
  clockOffset: number
}

/**
 * 대기방 — 방이 열린(WAITING) 뒤부터 입찰이 열리기 전까지.
 *
 * 진행중과 골격을 공유하지 않는다. 여기서 할 수 있는 일이 없어 채울 값도 접속자 수 하나뿐이라
 * 진행중의 3단 대시보드를 빌려 오면 빈 칸만 늘어난다. 성긴 화면이 빽빽한 진행중과 대비되면서
 * 시작 순간이 화면 밀도로 읽힌다.
 *
 * 잠긴 입찰 자리를 미리 잡아 두지 않는 것도 같은 이유다 — 누를 수 없는 버튼에는 자리를
 * 지켜 줄 손이 없어, 자리를 고정해서 얻는 것이 없다.
 *
 * phase는 구독이 밀어주므로 여기서 자동 전환을 흉내내지 않는다 —
 * 다음 전송에서 phase가 LIVE로 바뀌면 페이지가 알아서 다른 화면을 그린다.
 */
export function WaitingRoom({ room, clockOffset }: WaitingRoomProps) {
  return (
    <div className="mx-auto max-w-3xl text-center">
      <p className="text-warning flex items-center justify-center gap-2 text-base font-semibold tracking-widest">
        <Lock className="size-4" />
        입찰 시작까지
      </p>

      <Countdown
        targetIso={room.startAt}
        offsetMs={clockOffset}
        className="text-warning mt-2 block text-6xl font-bold"
      />

      <p className="text-muted-foreground mt-3 text-xl">
        {formatClock(room.startAt)}에 입찰이 시작됩니다 · 시작가{' '}
        <span className="text-foreground tabular font-semibold">
          {formatKRW(room.startPrice)}
        </span>
      </p>

      {/* 대기중에 변하는 값은 이것 하나뿐이다, 유일한 실시간 신호라 크게 둔다 */}
      <p className="text-muted-foreground mt-4 inline-flex items-center gap-2 rounded-full border px-6 py-2.5 text-lg">
        <Eye className="size-5" />
        지금 <span className="text-foreground tabular font-bold">{room.connectedCount}명</span>이
        함께 기다립니다
      </p>

      <CarPhotos
        model={room.vehicle.model}
        imageUrls={room.vehicle.imageUrls}
        aspectClassName="aspect-[16/6]"
        className="mt-5"
      />

      <div className="text-muted-foreground mt-4 flex flex-wrap items-center justify-center gap-x-7 gap-y-3 text-lg">
        <span>{room.vehicle.modelYear}년</span>
        <span className="text-foreground tabular font-semibold">
          {formatMileage(room.vehicle.mileage)}
        </span>
        <span>{FUEL_TYPE_LABEL[room.vehicle.fuelType]}</span>

        {/* 새 탭으로 연다, 방 위에 띄우면 읽는 동안 남은 시간이 가려진다 */}
        <Button asChild variant="ghost" className="text-base">
          <a href={room.vehicle.diagnosticReportUrl} target="_blank" rel="noreferrer">
            <FileText />
            진단서 보기
          </a>
        </Button>
      </div>
    </div>
  )
}
