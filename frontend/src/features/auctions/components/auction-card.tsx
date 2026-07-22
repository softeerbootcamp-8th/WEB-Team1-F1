import { Link } from 'react-router-dom'
import { Gauge, MapPin, Users } from 'lucide-react'

import { Card } from '@/components/ui/card'
import { CarThumb } from '@/components/common/car-thumb'
import { StatusBadge } from '@/components/common/status-badge'
import { Countdown } from '@/components/common/countdown'
import { formatKRW, formatManwon, formatMileage } from '@/lib/format'
import type { AuctionCard as AuctionCardModel } from '@/types/domain'

interface AuctionCardProps {
  auction: AuctionCardModel
}

/** 홈 리스트의 경매 카드. 썸네일·차종·현재가/시작가·남은시간. */
export function AuctionCard({ auction }: AuctionCardProps) {
  const { car, status } = auction
  const isLive = status === 'LIVE'
  const priceLabel = isLive ? '현재가' : status === 'ENDED' ? '낙찰가' : '시작가'
  const price = isLive || status === 'ENDED' ? auction.currentPrice : auction.startPrice

  return (
    <Card className="group gap-0 overflow-hidden py-0 transition-shadow hover:shadow-md">
      <Link to={`/auctions/${auction.id}`} className="block">
        <div className="bg-muted relative aspect-[4/3] overflow-hidden">
          <CarThumb
            src={auction.thumbnailUrl}
            alt={car.name}
            className="transition-transform duration-500 group-hover:scale-105"
          />
          <div className="absolute top-3 left-3">
            <StatusBadge status={status} />
          </div>
          {status === 'SCHEDULED' && (
            <div className="bg-background/85 absolute right-3 bottom-3 rounded-md px-2 py-1 text-xs backdrop-blur">
              <span className="text-muted-foreground">시작까지 </span>
              <Countdown targetIso={auction.startAt} className="font-medium" />
            </div>
          )}
          {isLive && (
            <div className="bg-background/85 absolute right-3 bottom-3 rounded-md px-2 py-1 text-xs backdrop-blur">
              <span className="text-muted-foreground">마감 </span>
              <Countdown targetIso={auction.endAt} className="font-medium" />
            </div>
          )}
        </div>
      </Link>

      <div className="flex flex-col gap-3 p-4">
        <div className="space-y-1">
          <Link to={`/auctions/${auction.id}`}>
            <h3 className="truncate font-semibold tracking-tight">{car.name}</h3>
          </Link>
          <p className="text-muted-foreground truncate text-xs">
            {auction.title}
          </p>
        </div>

        <dl className="text-muted-foreground flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
          <div className="flex items-center gap-1">
            <span className="tabular">{car.year}년</span>
          </div>
          <span aria-hidden>·</span>
          <div className="flex items-center gap-1">
            <Gauge className="size-3.5" />
            <span className="tabular">{formatMileage(car.mileageKm)}</span>
          </div>
          <span aria-hidden>·</span>
          <div className="flex items-center gap-1">
            <MapPin className="size-3.5" />
            <span>{car.region}</span>
          </div>
        </dl>

        <div className="flex items-end justify-between border-t pt-3">
          <div>
            <p className="text-muted-foreground text-xs">{priceLabel}</p>
            <p className="tabular text-xl font-semibold tracking-tight">
              {formatManwon(price)}
              <span className="text-muted-foreground ml-1 text-sm font-normal">
                원
              </span>
            </p>
            <p className="text-muted-foreground/70 tabular text-[11px]">
              {formatKRW(price)}
            </p>
          </div>
          <div className="text-muted-foreground flex items-center gap-1 text-xs">
            <Users className="size-3.5" />
            <span className="tabular">{auction.bidCount}</span>
          </div>
        </div>
      </div>
    </Card>
  )
}
