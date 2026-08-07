import { useEffect, useRef, type CSSProperties } from 'react'
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
import { useScrollReveal } from '@/hooks/use-scroll-reveal'

const FEATURES = [
  {
    icon: Search,
    title: '내 차 시세 조회',
    description: '차량번호와 주행거리로 내 차의 예상 시세를 확인하세요.',
    to: '/quote',
    cta: '시세 조회',
  },
  {
    icon: Gavel,
    title: '진행 중인 경매',
    description: '검증된 차량의 현재가와 남은 시간을 실시간으로 확인하세요.',
    to: '/auctions',
    cta: '경매 보기',
  },
  {
    icon: CarFront,
    title: '내 차 판매 신청',
    description: '차량 확인부터 방문 평가 연결까지 간편하게 신청하세요.',
    to: '/sell',
    cta: '판매 신청',
  },
]

export function HomePage() {
  const mainRef = useRef<HTMLElement>(null)

  useScrollReveal(mainRef)

  useEffect(() => {
    const main = mainRef.current

    if (
      !main ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      return
    }

    let animationFrame = 0

    const updateParallax = () => {
      animationFrame = 0
      const shift = Math.min(window.scrollY * 0.16, 112)
      main.style.setProperty('--hero-shift', `${shift}px`)
    }

    const onScroll = () => {
      if (animationFrame) return
      animationFrame = window.requestAnimationFrame(updateParallax)
    }

    updateParallax()
    window.addEventListener('scroll', onScroll, { passive: true })

    return () => {
      window.removeEventListener('scroll', onScroll)
      window.cancelAnimationFrame(animationFrame)
    }
  }, [])

  // 진행중만 서버에서 걸러 받는다. 첫 페이지에 진행중이 없으면 빈 손이던 문제가 사라진다.
  const { cards } = useAuctionList({ scope: 'ALL', filter: 'LIVE' })
  const liveAuctions = cards.slice(0, 3)

  return (
    <main ref={mainRef} aria-label="RACE 홈" className="home-page">
      <section className="relative isolate overflow-hidden bg-[#080a0b] text-white">
        <CinematicCarBackdrop
          className="home-hero-media -z-20"
          imageClassName="home-hero-image object-[center_68%] opacity-75 md:object-[center_62%] lg:object-center lg:opacity-85"
          sizes="100vw"
        />
        <div className="absolute inset-0 -z-10 bg-linear-to-r from-black/95 via-black/70 to-black/20 md:via-black/55" />
        <div className="absolute inset-0 -z-10 bg-linear-to-t from-black/80 via-transparent to-black/20" />
        <div className="relative mx-auto flex min-h-[max(680px,100svh)] max-w-7xl flex-col justify-center px-6 pt-28 pb-24 md:pt-32 md:pb-28">
          <p className="hero-enter hero-enter-1 mb-7 flex items-center gap-4 text-lg font-semibold text-white/85 md:text-xl">
            <span className="h-px w-12 bg-white/55" aria-hidden />
            중고차 실시간 경매
          </p>
          <h1 className="hero-enter hero-enter-2 max-w-6xl text-[2.75rem] leading-[1.08] font-semibold tracking-[-0.04em] sm:text-5xl md:text-7xl lg:whitespace-nowrap">
            <span className="block lg:inline">내 차를 경매로</span>{' '}
            <span className="block lg:inline">팔아보세요</span>
          </h1>
          <p className="hero-enter hero-enter-4 mt-7 max-w-xl text-base leading-7 text-white/75 md:text-lg">
            평가사가 차량 상태를 확인하면 검증된 구매자가 실시간으로
            입찰합니다. 시세 조회와 판매 신청도 바로 시작할 수 있습니다.
          </p>
          <div className="hero-enter hero-enter-5 mt-10 flex flex-wrap gap-3">
            <Button
              size="xl"
              variant="secondary"
              className="home-cta-fill group/cta"
              asChild
            >
              <Link to="/sell">
                내 차 팔기
                <ArrowRight className="size-4 transition-transform duration-300 group-hover/cta:translate-x-1" />
              </Link>
            </Button>
            <Button
              size="xl"
              variant="outline"
              className="group/cta border-white/25 bg-black/25 text-white transition-all duration-300 hover:-translate-y-0.5 hover:border-white/50 hover:bg-white/10 hover:text-white"
              asChild
            >
              <Link to="/quote">
                시세 조회
                <ArrowRight className="size-4 transition-transform duration-300 group-hover/cta:translate-x-1" />
              </Link>
            </Button>
          </div>
        </div>
      </section>

      <section id="why-race" className="bg-[#f0f0ed]">
        <div className="mx-auto grid max-w-7xl gap-12 px-6 py-24 lg:grid-cols-[0.9fr_1.1fr] lg:py-32">
          <div data-reveal>
            <p className="text-muted-foreground text-sm font-medium">
              판매 과정
            </p>
            <h2 className="mt-4 text-3xl leading-tight font-semibold md:text-5xl">
              차량을 확인하고,
              <br />
              입찰로 가격을 정합니다
            </h2>
          </div>
          <div className="grid gap-px overflow-hidden rounded-xl border bg-border sm:grid-cols-2">
            <Value
              icon={BadgeCheck}
              title="평가사가 직접 확인"
              description="차량 상태와 핵심 정보를 현장에서 확인합니다."
              delay={0}
            />
            <Value
              icon={ChartNoAxesCombined}
              title="입찰로 정해지는 가격"
              description="검증된 구매자의 입찰로 판매 가격이 결정됩니다."
              delay={80}
            />
            <Value
              icon={ShieldCheck}
              title="공개되는 입찰 과정"
              description="현재가와 입찰 흐름을 같은 기준으로 확인합니다."
              delay={160}
            />
            <Value
              icon={CarFront}
              title="낙찰 후 거래까지"
              description="판매 신청부터 낙찰 이후 거래 단계까지 이어집니다."
              delay={240}
            />
          </div>
        </div>
      </section>

      <section className="bg-[#e4e4e0]">
        <div className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
          <div className="mb-12 max-w-2xl" data-reveal>
            <p className="text-muted-foreground text-sm font-medium">
              바로 시작하기
            </p>
            <h2 className="mt-4 text-3xl font-semibold md:text-5xl">
              무엇을 도와드릴까요?
            </h2>
          </div>
          <div className="grid gap-4 lg:grid-cols-3">
            {FEATURES.map((feature, index) => {
              const Icon = feature.icon
              return (
                <Link
                  key={feature.title}
                  to={feature.to}
                  data-reveal
                  aria-label={feature.title}
                  style={
                    {
                      '--reveal-delay': `${index * 90}ms`,
                    } as CSSProperties
                  }
                  className="home-feature-card group bg-background relative flex min-h-72 flex-col overflow-hidden rounded-xl border p-7 outline-none focus-visible:ring-2 focus-visible:ring-foreground/60 focus-visible:ring-offset-4"
                >
                  <span className="home-feature-edge" aria-hidden />
                  <div className="flex items-start justify-between">
                    <Icon className="size-6" />
                    <span className="text-muted-foreground tabular text-xs">
                      {String(index + 1).padStart(2, '0')}
                    </span>
                  </div>
                  <h3 className="mt-12 text-2xl font-semibold">{feature.title}</h3>
                  <p className="text-muted-foreground mt-3 leading-6">
                    {feature.description}
                  </p>
                  <span className="mt-auto flex items-center gap-2 pt-8 text-sm font-semibold">
                    {feature.cta}
                    <ArrowRight className="size-4 transition-transform duration-300 group-hover:translate-x-1.5" />
                  </span>
                </Link>
              )
            })}
          </div>
        </div>
      </section>

      <section id="live-auctions" className="mx-auto max-w-7xl px-6 py-24 lg:py-32">
        <div
          className="mb-10 flex flex-wrap items-end justify-between gap-5"
          data-reveal
        >
          <div>
            <p className="text-muted-foreground flex items-center gap-2 text-sm font-medium">
              <span className="bg-live size-2 rounded-full motion-safe:animate-live-pulse" />
              실시간 경매
            </p>
            <h2 className="mt-3 text-3xl font-semibold md:text-5xl">
              지금 입찰 중인 차량
            </h2>
          </div>
          <Button variant="outline" asChild>
            <Link to="/auctions">
              경매 전체 보기
              <ArrowRight className="size-4" />
            </Link>
          </Button>
        </div>
        <ul className="grid gap-5 md:grid-cols-2 lg:grid-cols-3">
          {liveAuctions.map((auction, index) => (
            <li
              key={auction.auctionId}
              data-reveal
              style={
                {
                  '--reveal-delay': `${index * 90}ms`,
                } as CSSProperties
              }
            >
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
  delay,
}: {
  icon: typeof ShieldCheck
  title: string
  description: string
  delay: number
}) {
  return (
    <article
      data-reveal
      style={{ '--reveal-delay': `${delay}ms` } as CSSProperties}
      className="home-value-card group bg-background p-7"
    >
      <Icon className="size-6" />
      <h3 className="mt-8 text-lg font-semibold">{title}</h3>
      <p className="text-muted-foreground mt-2 text-sm leading-6 transition-colors duration-500 group-hover:text-white/65">
        {description}
      </p>
    </article>
  )
}
