import { Link } from 'react-router-dom'
import {
  ArrowRight,
  BadgeCheck,
  CarFront,
  ChartNoAxesCombined,
  Gavel,
  Search,
  ShieldCheck,
} from 'lucide-react'

import { Button } from '@/components/ui/button'
import { CinematicCarBackdrop } from '@/components/common/cinematic-car-backdrop'
import { AuctionCard } from '@/features/auctions/components/auction-card'
import { useAuctionList } from '@/features/auctions/use-auction-list'

const FEATURES = [
  {
    icon: Search,
    eyebrow: 'PRICE',
    title: '로그인 없이 시세 조회',
    description: '이름과 번호판으로 차량을 확인한 뒤, 현재 주행거리를 반영해 예상 시세를 확인합니다.',
    to: '/quote',
    cta: '시세 확인하기',
  },
  {
    icon: Gavel,
    eyebrow: 'AUCTION',
    title: '실시간 경매 참여',
    description: '검증된 차량의 현재가와 입찰 흐름을 투명하게 확인하세요.',
    to: '/auctions',
    cta: '경매 둘러보기',
  },
  {
    icon: CarFront,
    eyebrow: 'SELL',
    title: '차량 정보로 판매 접수',
    description: '복잡한 차량 정보 없이 소유자와 차량을 확인하고 평가사를 연결합니다.',
    to: '/sell',
    cta: '내 차 팔기',
  },
]

export function HomePage() {
  // 진행중만 서버에서 걸러 받는다. 첫 페이지에 진행중이 없으면 빈 손이던 문제가 사라진다.
  const { cards } = useAuctionList({ scope: 'ALL', filter: 'LIVE' })
  const liveAuctions = cards.slice(0, 3)

  return (
    <main aria-label="RACE 홈">
      <section className="relative isolate overflow-hidden bg-[#080a0b] text-white">
        <CinematicCarBackdrop
          className="-z-20"
          imageClassName="object-[center_68%] opacity-75 md:object-[center_62%] lg:object-center lg:opacity-85"
          sizes="100vw"
        />
        <div className="absolute inset-0 -z-10 bg-linear-to-r from-black/95 via-black/70 to-black/20 md:via-black/55" />
        <div className="absolute inset-0 -z-10 bg-linear-to-t from-black/80 via-transparent to-black/20" />
        <div className="relative mx-auto flex min-h-[620px] max-w-7xl flex-col justify-center px-6 py-20 md:min-h-[700px] md:py-24">
          <p className="mb-6 w-fit rounded-full border border-white/15 bg-black/20 px-4 py-2 text-xs font-medium tracking-[0.24em] text-white/70 uppercase backdrop-blur-md md:text-sm">
            Real Time Auction Car Exchange
          </p>
          <h1 className="max-w-4xl text-5xl leading-[1.08] font-semibold tracking-[-0.04em] text-balance md:text-7xl">
            중고차 가격이 만들어지는
            <br />
            모든 순간을 투명하게.
          </h1>
          <p className="mt-7 max-w-xl text-base leading-7 text-white/75 md:text-lg">
            평가사가 확인한 차량을 실시간 경매에 연결합니다. 판매자는 더
            나은 가격을, 구매자는 더 분명한 근거를 확인하세요.
          </p>
          <div className="mt-10 flex flex-wrap gap-3">
            <Button size="xl" variant="secondary" asChild>
              <Link to="/sell">
                내 차 판매 접수
                <ArrowRight className="size-4" />
              </Link>
            </Button>
            <Button
              size="xl"
              variant="outline"
              className="border-white/25 bg-black/15 text-white backdrop-blur-sm hover:bg-white/10 hover:text-white"
              asChild
            >
              <Link to="/quote">내 차 시세 조회</Link>
            </Button>
          </div>
        </div>
      </section>

      <section className="mx-auto grid max-w-7xl gap-12 px-6 py-24 lg:grid-cols-[0.9fr_1.1fr] lg:py-32">
        <div>
          <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
            Why RACE
          </p>
          <h2 className="mt-4 text-3xl leading-tight font-semibold md:text-5xl">
            감이 아니라,
            <br />
            참여로 만드는 가격
          </h2>
        </div>
        <div className="grid gap-px overflow-hidden rounded-2xl border bg-border sm:grid-cols-2">
          <Value
            icon={ChartNoAxesCombined}
            title="실시간 가격 형성"
            description="모든 입찰 흐름과 현재가를 같은 기준으로 공개합니다."
          />
          <Value
            icon={BadgeCheck}
            title="평가사 진단 기반"
            description="차량 상태와 핵심 정보를 평가사가 직접 확인합니다."
          />
          <Value
            icon={ShieldCheck}
            title="검증된 참여자"
            description="회원 유형을 구분하고 거래 단계를 끝까지 추적합니다."
          />
          <Value
            icon={CarFront}
            title="간결한 판매 접수"
            description="이름·번호판 확인 후 필요한 정보는 방문 평가에서 완성합니다."
          />
        </div>
      </section>

      <section className="bg-muted/55">
        <div className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
          <div className="mb-12 max-w-2xl">
            <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
              One Flow
            </p>
            <h2 className="mt-4 text-3xl font-semibold md:text-5xl">
              필요한 기능으로 바로 이동하세요
            </h2>
          </div>
          <div className="grid gap-4 lg:grid-cols-3">
            {FEATURES.map((feature) => {
              const Icon = feature.icon
              return (
                <article
                  key={feature.title}
                  className="bg-background flex min-h-80 flex-col rounded-2xl border p-7"
                >
                  <div className="bg-foreground text-background flex size-11 items-center justify-center rounded-full">
                    <Icon className="size-5" />
                  </div>
                  <p className="text-muted-foreground mt-10 text-xs tracking-[0.18em]">
                    {feature.eyebrow}
                  </p>
                  <h3 className="mt-3 text-2xl font-semibold">{feature.title}</h3>
                  <p className="text-muted-foreground mt-3 leading-6">
                    {feature.description}
                  </p>
                  <Link
                    to={feature.to}
                    className="mt-auto flex items-center gap-2 pt-8 text-sm font-semibold"
                  >
                    {feature.cta}
                    <ArrowRight className="size-4" />
                  </Link>
                </article>
              )
            })}
          </div>
        </div>
      </section>

      <section id="live-auctions" className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div className="mb-10 flex flex-wrap items-end justify-between gap-5">
          <div>
            <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
              Live Now
            </p>
            <h2 className="mt-3 text-3xl font-semibold md:text-5xl">
              지금 진행 중인 경매
            </h2>
          </div>
          <Button variant="outline" asChild>
            <Link to="/auctions">
              전체 경매 보기
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
        <ul className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {liveAuctions.map((auction) => (
            <li key={auction.auctionId}>
              <AuctionCard auction={auction} />
            </li>
          ))}
        </ul>
      </section>
    </main>
  )
}

function Value({
  icon: Icon,
  title,
  description,
}: {
  icon: typeof ShieldCheck
  title: string
  description: string
}) {
  return (
    <article className="bg-background p-7">
      <Icon className="size-5" />
      <h3 className="mt-8 text-lg font-semibold">{title}</h3>
      <p className="text-muted-foreground mt-2 text-sm leading-6">
        {description}
      </p>
    </article>
  )
}
