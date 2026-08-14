import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, CarFront, Gavel, Search, Trophy } from 'lucide-react'

import { CinematicCarBackdrop } from '@/components/common/cinematic-car-backdrop'
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
        <div className="relative mx-auto flex min-h-[max(680px,100svh)] max-w-7xl -translate-y-16 flex-col justify-center px-6 pt-28 pb-24 md:-translate-y-20 md:pt-32 md:pb-28">
          <p className="hero-enter hero-enter-1 mb-7 flex items-center gap-4 text-xl font-semibold text-white/85 md:text-2xl">
            Realtime Auction Car Exchange
          </p>
          <h1 className="hero-enter hero-enter-2 max-w-6xl text-[2.75rem] leading-[1.08] font-semibold tracking-[-0.04em] sm:text-5xl md:text-7xl lg:whitespace-nowrap">
            <span className="block lg:inline">누구나 사고 팔 수 있는</span>{' '}
            <span className="block lg:inline">중고차 경매</span>
          </h1>
          <p className="hero-enter hero-enter-4 mt-7 max-w-xl text-lg leading-7 text-white/75 md:text-xl">
            <span className="block">
              평가사가 검증한 차량을 모두가 실시간으로 입찰할 수 있습니다.
            </span>
            <span className="block">
              모든 입찰 내역이 투명한 경매를 경험해보세요.
            </span>
          </p>
        </div>
      </section>

      <section
        id="selling-process"
        aria-label="RACE 차량 판매 과정"
        className="bg-[#f3f6f8]"
      >
        <div className="mx-auto w-full max-w-7xl px-6 py-20 lg:py-24">
          <div className="mb-10 max-w-2xl" data-reveal>
            <h2 className="text-3xl font-semibold md:text-5xl">
              방문부터 거래까지
            </h2>
          </div>
          <div
            data-reveal
            className="grid w-full gap-6 md:grid-cols-2 xl:grid-cols-4"
          >
          <ProcessCard
            step="1"
            title="원하는 곳, 방문 평가"
            description={
              <>
                원하는 일정에 평가사가 방문
                <br />
                차량 진단부터 경매 등록까지
              </>
            }
          >
            <div className="rounded-xl border border-slate-200 bg-white px-5 pt-5 pb-10 shadow-sm">
              <div className="flex items-center justify-between border-b border-slate-100 pb-4">
                <div>
                  <p className="text-[0.65rem] font-medium tracking-widest text-slate-400">
                    방문 희망일
                  </p>
                  <p className="mt-1 text-sm font-semibold text-slate-800">
                    언제 방문드릴까요?
                  </p>
                </div>
                <span className="flex size-8 items-center justify-center rounded-full bg-[#202225] text-xs text-white">
                  14
                </span>
              </div>
              <p className="mt-5 text-center text-xs font-semibold text-slate-500">
                2026년 8월
              </p>
              <div className="mt-4 grid grid-cols-7 gap-y-3 text-center text-[0.65rem] text-slate-400">
                {['일', '월', '화', '수', '목', '금', '토'].map((day) => (
                  <span key={day}>{day}</span>
                ))}
                {[10, 11, 12, 13, 14, 15, 16].map((date) => (
                  <span
                    key={date}
                    className={
                      date === 14
                        ? 'mx-auto flex size-7 items-center justify-center rounded-full bg-[#202225] text-white shadow-sm ring-2 ring-[#c8cbce] ring-offset-2'
                        : 'flex h-7 items-center justify-center text-slate-700'
                    }
                  >
                    {date}
                  </span>
                ))}
              </div>
            </div>
          </ProcessCard>

          <ProcessCard
            step="2"
            title="20분 실시간 경매"
            description={
              <>
                언제 어디서나 실시간 경매 참여
                <br />
                현재가와 호가를 바로 확인
              </>
            }
          >
            <div className="-mx-2 flex items-center justify-between px-1">
              <p className="text-sm font-semibold text-slate-800">호가창</p>
              <p className="text-xs text-slate-400">
                총 <span className="font-medium text-slate-700">18</span>건 입찰
              </p>
            </div>
            <ul className="-mx-2 mt-3 divide-y divide-slate-200 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
              {[
                ['김*인', '5,230만원', '오전', '11:24'],
                ['이*민', '5,210만원', '오전', '11:22'],
                ['박*수', '5,190만원', '오전', '11:20'],
                ['최*호', '5,170만원', '오전', '11:18'],
                ['정*우', '5,150만원', '오전', '11:16'],
              ].map(([name, price, period, time], index) => (
                <li
                  key={`${name}-${price}`}
                  className={`grid grid-cols-[auto_minmax(0,1fr)_2.25rem_auto_1.8rem] items-center gap-1.5 px-2.5 py-2 ${index === 0 ? 'bg-[#eef0ee]' : ''}`}
                >
                  <span className="rounded-full border border-slate-200 px-1.5 py-0.5 text-[0.55rem] text-slate-500">
                    일반
                  </span>
                  <span className="truncate text-[0.68rem] font-semibold text-slate-800">
                    {name}
                  </span>
                  <span className="flex justify-end">
                    {index === 0 && (
                      <span className="rounded-full bg-[#dfe3df] px-1.5 py-0.5 text-[0.5rem] font-semibold whitespace-nowrap text-[#39413b]">
                        최고가
                      </span>
                    )}
                  </span>
                  <strong className="text-right text-[0.68rem] whitespace-nowrap text-slate-900">
                    {price}
                  </strong>
                  <time className="flex flex-col items-end text-[0.52rem] leading-tight text-slate-400 tabular-nums">
                    <span>{period}</span>
                    <span>{time}</span>
                  </time>
                </li>
              ))}
            </ul>
          </ProcessCard>

          <ProcessCard
            step="3"
            title="최고가 자동 낙찰"
            description={
              <>
                판매자 승낙을 기다리지 않고
                <br />
                최고 입찰가로 바로 확정
              </>
            }
          >
            <div className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
              <div className="flex items-center gap-3 border-b border-slate-100 pb-4">
                <span className="flex size-10 items-center justify-center rounded-full bg-[#202225] text-white">
                  <Trophy className="size-5" aria-hidden />
                </span>
                <div>
                  <p className="text-sm font-bold text-slate-900">
                    차가 팔렸어요
                  </p>
                  <p className="mt-0.5 text-[0.65rem] text-slate-400">
                    최고 입찰가로 낙찰 완료
                  </p>
                </div>
              </div>
              <div className="mt-5">
                <p className="text-[0.65rem] font-medium tracking-widest text-slate-400">
                  최종 낙찰가
                </p>
                <p className="mt-1 text-2xl font-bold tracking-tight text-slate-900">
                  5,230만원
                </p>
              </div>
            </div>
            <div className="mt-3 rounded-md bg-[#202225] py-3 text-center text-xs font-semibold text-white shadow-sm">
              거래 진행하기
            </div>
          </ProcessCard>

          <article
            className="flex h-[30rem] flex-col items-center justify-center rounded-xl border border-white/10 bg-linear-to-br from-[#34373a] via-[#181a1c] to-[#050606] px-8 py-10 text-center text-white shadow-[0_20px_50px_rgba(0,0,0,0.2)]"
            >
              <span className="text-6xl font-semibold tracking-[-0.05em] text-[#d8dadd]">
                RACE
              </span>
              <h3 className="mt-8 text-2xl leading-tight font-semibold">
                낙찰 이후 거래까지
                <br />한 번에 이어집니다
              </h3>
              <p className="mt-5 text-base leading-7 text-[#b8bcc1]">
                평가사 진단 · 등록 · 경매 · 거래
              </p>
            </article>
          </div>
        </div>
      </section>

      <section className="bg-[#f7f5f0]">
        <div className="mx-auto w-full max-w-7xl px-6 py-20 lg:py-24">
          <div className="mb-10 max-w-2xl" data-reveal>
            <h2 className="text-3xl font-semibold md:text-5xl">
              바로 시작하기
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
    </main>
  )
}

function ProcessCard({
  step,
  title,
  description,
  children,
}: {
  step: string
  title: string
  description: ReactNode
  children: ReactNode
}) {
  return (
    <article
      className="flex h-[30rem] flex-col overflow-hidden rounded-xl bg-white px-6 pt-8 shadow-[0_20px_50px_rgba(15,23,42,0.08)]"
    >
      <span className="flex size-7 items-center justify-center rounded bg-[#202225] text-sm font-semibold text-white shadow-sm ring-1 ring-[#5b5f63]">
        {step}
      </span>
      <h3 className="mt-4 min-h-14 text-2xl leading-tight font-semibold tracking-[-0.04em]">
        {title}
      </h3>
      <p className="mt-2 min-h-16 text-sm leading-6 text-slate-700">
        {description}
      </p>
      <div className="mt-5 h-64 shrink-0 rounded-t-[1.75rem] bg-[#f5f7fa] px-5 pt-7 pb-6">
        {children}
      </div>
    </article>
  )
}
