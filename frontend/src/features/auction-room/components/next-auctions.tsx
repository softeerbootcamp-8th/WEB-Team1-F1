import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Car } from 'lucide-react'

import { Countdown } from '@/components/common/countdown'
import { fetchAuctionList } from '@/features/auctions/api'
import type { AuctionListCard } from '@/features/auctions/types'
import { badgeStatusAt } from '@/lib/auction'
import { formatClock, formatManwon, formatMileage } from '@/lib/format'
import { MANUFACTURER_LABEL } from '@/features/quote/types'

const SHOWN = 2

/**
 * 결과를 다 본 사람에게 이어서 볼 경매를 권한다.
 *
 * 아직 "비슷한" 기준이 없어(#256) 목록 앞쪽을 그대로 가져온다. 서버가 진행중을 먼저, 그다음
 * 예정을 주므로 지금 들어갈 수 있는 것이 앞에 온다. 기준이 생기면 이 조회만 바뀐다.
 */
export function NextAuctions({ exceptAuctionId }: { exceptAuctionId: number }) {
  const [cards, setCards] = useState<AuctionListCard[]>([])

  useEffect(() => {
    let cancelled = false

    // 필터를 걸지 않아 서버가 진행중, 예정, 종료 순서로 준다
    fetchAuctionList({ scope: 'ALL', filter: null })
      .then((page) => {
        if (cancelled) return

        // 끝난 경매를 권하면 눌러도 갈 곳이 없다, 방금 본 경매도 뺀다
        setCards(
          page.content
            .filter((card) => card.auctionId !== exceptAuctionId)
            .filter((card) => badgeStatusAt(card, Date.now()) !== 'ENDED')
            .slice(0, SHOWN),
        )
      })
      // 권하는 자리라 실패해도 결과 화면은 그대로 서야 한다, 조용히 비워 둔다
      .catch(() => undefined)

    return () => {
      cancelled = true
    }
  }, [exceptAuctionId])

  if (cards.length === 0) return null

  return (
    <section className="mt-6 grid gap-4 sm:grid-cols-2" aria-label="이어서 볼 경매">
      {cards.map((card) => (
        <NextAuctionCard key={card.auctionId} card={card} />
      ))}
    </section>
  )
}

function NextAuctionCard({ card }: { card: AuctionListCard }) {
  const status = badgeStatusAt(card, Date.now())

  return (
    <Link
      to={`/auctions/${card.auctionId}`}
      className="hover:bg-muted/40 flex items-center gap-4 rounded-xl border p-4 transition-colors"
    >
      <span className="bg-muted text-muted-foreground flex size-12 shrink-0 items-center justify-center overflow-hidden rounded-lg">
        {card.thumbnailUrl ? (
          <img src={card.thumbnailUrl} alt="" className="size-full object-cover" />
        ) : (
          <Car className="size-5" aria-hidden />
        )}
      </span>

      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">
          {MANUFACTURER_LABEL[card.manufacturer]} {card.model}
        </p>
        <p className="text-muted-foreground text-sm">
          {card.modelYear}년 · {formatMileage(card.mileage)}
        </p>
      </div>

      <div className="shrink-0 text-right">
        <p className="tabular font-semibold">{formatManwon(card.currentPrice)}원</p>
        <p className="text-muted-foreground text-sm">
          {status === 'LIVE' ? (
            <span className="text-destructive">
              진행중 · <Countdown targetIso={card.endAt} /> 남음
            </span>
          ) : (
            `예정 · ${formatClock(card.startAt)} 시작`
          )}
        </p>
      </div>
    </Link>
  )
}
