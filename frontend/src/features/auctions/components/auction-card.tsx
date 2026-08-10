import { Link } from 'react-router-dom'
import { Eye, Gauge } from 'lucide-react'

import { Card } from '@/components/ui/card'
import { CarThumb } from '@/components/common/car-thumb'
import { StatusBadge } from '@/components/common/status-badge'
import { Countdown } from '@/components/common/countdown'
import { formatManwon, formatMileage } from '@/lib/format'
import { badgeStatusAt } from '@/lib/auction'
import type { AuctionListCard as AuctionListCardModel } from '@/features/auctions/types'

interface AuctionCardProps {
  auction: AuctionListCardModel
  /** 서버 기준 현재 시각. 목록이 굴리는 시계 하나를 받아 쓴다(useServerClock) */
  nowMs: number
  /** 서버 시각 - 브라우저 시계. 남은 시간을 브라우저가 아니라 서버 기준으로 센다 */
  offsetMs?: number
  /** 카드 하단에 덧붙일 조작 영역. 나의 경매에서 수정·삭제 버튼을 넣는다. */
  actions?: React.ReactNode
}

/** 홈/목록 화면의 경매 카드. 썸네일·차종·현재가/시작가·남은시간. */
export function AuctionCard({ auction, nowMs, offsetMs = 0, actions }: AuctionCardProps) {
  // 서버가 준 phase 를 쓰지 않는다. 조회 시각의 값이라 화면을 열어 둔 동안 낡는다
  const status = badgeStatusAt(auction, nowMs)
  const isLive = status === 'LIVE'
  // 입장 여부와 상관없이 아직 입찰 전이라 값은 똑같이 시작가를 보여준다
  const isBeforeStart = status === 'NOT_OPEN' || status === 'WAITING'
  const priceLabel = isLive ? '현재가' : status === 'ENDED' ? '낙찰가' : '시작가'
  const price = isBeforeStart ? auction.startPrice : auction.currentPrice

  return (
    <Card className="group gap-0 overflow-hidden py-0 transition-[transform,box-shadow,border-color] duration-500 ease-out hover:-translate-y-1.5 hover:border-foreground/20 hover:shadow-xl hover:shadow-black/10">
      <Link to={`/auctions/${auction.auctionId}`} className="block">
        {/* 2열 구간에서는 사진을 크게 눕힌다. 열이 넓어 4:3이면 사진 한 장이 화면 높이를
            다 먹고, 한 화면에 두 장밖에 남지 않아 고를 대상이 눈에 들어오지 않는다 */}
        <div className="bg-muted relative aspect-[4/3] overflow-hidden md:aspect-[5/2]">
          <CarThumb
            src={auction.thumbnailUrl ?? undefined}
            alt={auction.model}
            className="transition-transform duration-500 group-hover:scale-105"
          />
          {/* 입장 전에도 개설 시각이 아니라 시작 시각을 센다. 개설은 시작 30분 전 고정이라
              따로 세어 봐야 새 정보가 없고, 카드마다 기준이 달라지면 남은 시간끼리 비교가 깨진다 */}
          {isBeforeStart && (
            <div className="bg-background/85 absolute right-3 bottom-3 rounded-md px-2 py-1 text-xs backdrop-blur">
              <span className="text-muted-foreground">시작까지 </span>
              <Countdown targetIso={auction.startAt} offsetMs={offsetMs} className="font-medium" />
            </div>
          )}
          {isLive && (
            <div className="bg-background/85 absolute right-3 bottom-3 rounded-md px-2 py-1 text-xs backdrop-blur">
              <span className="text-muted-foreground">마감 </span>
              <Countdown targetIso={auction.endAt} offsetMs={offsetMs} className="font-medium" />
            </div>
          )}
        </div>
      </Link>

      <div className="flex flex-col gap-2 p-4">
        <Link to={`/auctions/${auction.auctionId}`}>
          <h3 className="truncate text-lg font-semibold tracking-tight md:text-xl">
            {auction.model}
          </h3>
        </Link>

        {/* 뱃지는 사진 위가 아니라 이 줄에 둔다. 제목 줄에 붙이면 트림이 긴 차종에서
            제목이 잘리는데, 연식·주행거리는 글자가 짧아 오른쪽이 늘 비어 있다 */}
        <div className="flex items-center justify-between gap-2">
          <dl className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
            <div className="flex items-center gap-1">
              <span className="tabular">{auction.modelYear}년</span>
            </div>
            <span aria-hidden>·</span>
            <div className="flex items-center gap-1">
              <Gauge className="size-4" />
              <span className="tabular">{formatMileage(auction.mileage)}</span>
            </div>
          </dl>
          <StatusBadge status={status} />
        </div>

        <div className="flex items-end justify-between gap-3 border-t pt-3">
          <div>
            <p className="text-muted-foreground text-xs">{priceLabel}</p>
            <p className="tabular text-2xl font-semibold tracking-tight">
              {formatManwon(price)}
              <span className="text-muted-foreground ml-1 text-base font-normal">
                원
              </span>
            </p>
          </div>
          <div className="text-muted-foreground flex items-center gap-1 text-xs">
            <Eye className="size-3.5" />
            <span className="tabular">실시간 시청 {auction.connectedCount}명</span>
          </div>
        </div>

        {actions && <div className="border-t pt-3">{actions}</div>}
      </div>
    </Card>
  )
}
