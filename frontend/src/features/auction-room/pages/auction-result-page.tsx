import { useMemo } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { CircleAlert, Clock, Gavel, Trophy } from 'lucide-react'

import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/empty-state'
import { similarFilter, toFilterParams } from '@/features/auctions/filter'
import { useAuth } from '@/features/auth/auth-context'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { formatClock, formatDuration, formatKRW } from '@/lib/format'
import { useCountdown } from '@/hooks/use-countdown'
import type { RoomResultView } from '@/features/auction-room/types'

import { BackLink } from '../components/back-link'
import { CarDetail } from '../components/car-detail'
import { PriceCurve } from '../components/price-curve'
import { curveShapeOf, viewerStandingOf } from '../result'
import { useAuctionResult } from '../use-auction-result'

/**
 * 끝난 경매의 결과.
 *
 * 경매방과 달리 구독하지 않고 단계도 보지 않는다. 결과는 더 이상 바뀌지 않으므로 한 번 받아
 * 그리고, 결과 확인 구간이 지난 뒤에도 이 주소로 들어오면 그대로 보인다.
 */
export function AuctionResultPage() {
  const { id } = useParams()
  const auctionId = Number(id)

  if (!Number.isFinite(auctionId)) {
    return <ResultNotice title="경매를 찾을 수 없습니다" description="잘못된 주소입니다." />
  }

  return <ResultContent auctionId={auctionId} />
}

function ResultContent({ auctionId }: { auctionId: number }) {
  const { result, entry } = useAuctionResult(auctionId)

  if (entry === 'SIGNED_OUT') {
    return <SignInNotice />
  }

  if (entry === 'BROKEN') {
    return (
      <ResultNotice title="경매를 찾을 수 없습니다" description="삭제되었거나 잘못된 주소입니다." />
    )
  }

  // 마감은 됐는데 낙찰자가 아직 확정되지 않은 짧은 구간이다. 훅이 몇 번 더 물어보는 중이다
  if (!result) {
    return entry === 'PENDING' ? (
      <ResultNotice title="결과를 확정하는 중이에요" description="곧 낙찰자가 정해집니다." />
    ) : (
      <main aria-label="경매 결과" className="mx-auto max-w-7xl px-6 py-8" />
    )
  }

  return (
    <main
      aria-label={`${result.vehicle.model} 경매 결과`}
      className="mx-auto max-w-7xl px-6 py-8"
    >
      <BackLink />

      <div className="mb-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-stretch gap-3">
          <span className="bg-price-up w-1 shrink-0 self-stretch rounded-full" aria-hidden />
          <div className="space-y-1">
            <span className="text-muted-foreground text-sm">{result.vehicle.modelYear}년</span>
            <h1 className="text-2xl font-semibold tracking-tight md:text-3xl">
              {MANUFACTURER_LABEL[result.vehicle.manufacturer]} {result.vehicle.model}
            </h1>
          </div>
        </div>

        <ViewingBadge result={result} />
      </div>

      {result.outcome === 'UNSOLD' ? <UnsoldBody result={result} /> : <SoldBody result={result} />}
    </main>
  )
}

/**
 * 낙찰된 경매. 값이 오른 과정과 최종가, 그리고 그 과정을 센 숫자들이 볼 것의 전부다.
 */
function SoldBody({ result }: { result: RoomResultView }) {
  return (
    <>
      <Headline result={result} />

      {/* 오른쪽 열이 곡선 높이까지 늘어난다, 아래끝이 어긋나면 요약이 곡선에 딸린 각주처럼 보인다 */}
      <div className="mt-6 grid items-stretch gap-6 lg:grid-cols-[1.75fr_1fr]">
        <CurveCard result={result} />

        <div className="flex flex-col gap-4">
          <WinningCard result={result} />
          <FactGrid result={result} />
        </div>
      </div>
    </>
  )
}

/**
 * 유찰된 경매. 낙찰 화면의 틀을 그대로 쓰지 않는다.
 *
 * 입찰이 한 건도 없으므로 곡선은 빈 상자가 되고 건수·참여자·연장은 모두 0이다. 아무것도 없었다는
 * 사실을 네 칸에 나눠 적을 이유가 없다. 대신 어떤 차가 왜 안 팔렸는지와 다음에 할 일을 둔다.
 */
function UnsoldBody({ result }: { result: RoomResultView }) {
  return (
    <>
      <Headline result={result} />

      <div className="mt-6 grid gap-6 lg:grid-cols-[1.75fr_1fr]">
        <CarDetail vehicle={result.vehicle} />

        <dl className="bg-border grid auto-rows-fr grid-cols-1 gap-px self-start overflow-hidden rounded-xl border">
          <div className="bg-card px-5 py-4">
            <dt className="text-muted-foreground text-sm">시작가</dt>
            <dd className="tabular mt-1 text-2xl font-semibold">
              {formatKRW(result.startPrice)}
            </dd>
          </div>
          <div className="bg-card px-5 py-4">
            <dt className="text-muted-foreground text-sm">마감</dt>
            <dd className="tabular mt-1 text-2xl font-semibold">{formatClock(result.endAt)}</dd>
          </div>
        </dl>
      </div>
    </>
  )
}

/** 결과를 얼마나 더 볼 수 있는지. 지나도 화면은 남으므로 문구만 바뀐다 */
function ViewingBadge({ result }: { result: RoomResultView }) {
  // 한 번만 잰다. 렌더마다 다시 재면 값이 미세하게 달라지고, 그것이 카운트다운의 의존성이라
  // 1초 간격이 채워지기 전에 타이머가 껐다 켜지기를 되풀이해 숫자가 제자리에 멈춘다
  const offset = useMemo(
    () => new Date(result.serverTime).getTime() - Date.now(),
    [result.serverTime],
  )
  const { remaining } = useCountdown(result.resultEndAt, 1000, offset)

  // 1초가 안 남으면 "00:00 남음"이 찍힌다, 그 표기가 나오기 전에 끝난 것으로 본다
  const viewing = remaining >= 1000

  return (
    <div className="border-price-up/40 bg-price-up/10 text-price-up flex items-center gap-2 rounded-xl border px-4 py-3 text-sm">
      <Clock className="size-4" aria-hidden />
      <span className="font-semibold">{viewing ? '방금 마감' : '종료된 경매'}</span>
      {viewing && (
        <span className="text-muted-foreground tabular">
          결과 열람 {formatDuration(remaining)} 남음
        </span>
      )}
    </div>
  )
}

/**
 * 첫 문장. 보는 사람이 선 자리에 따라 넷으로 갈린다.
 *
 * 낙찰인지 유찰인지는 자리와 따로 본다. 그래야 판매자와 구경꾼이 유찰을 각자의 말로 듣는다.
 */
function Headline({ result }: { result: RoomResultView }) {
  const { user } = useAuth()
  const standing = viewerStandingOf(result)
  const unsold = result.outcome === 'UNSOLD'

  const gap =
    result.myBid && result.winningPrice !== null ? result.winningPrice - result.myBid.amount : null

  // 유찰이면 팔린 값이 없어 시작가로 범위를 잡는다
  const similarPath = `/auctions?${toFilterParams(
    similarFilter(result.vehicle.manufacturer, result.winningPrice ?? result.startPrice),
  )}`

  const title = unsold
    ? standing === 'SELLER'
      ? '이번엔 팔리지 않았어요'
      : '입찰 없이 유찰됐어요'
    : {
        WON: '낙찰받았어요',
        LOST: '아쉽게 놓쳤어요',
        SELLER: '차가 팔렸어요',
        ONLOOKER: '경매가 끝났어요',
      }[standing]

  // 구경꾼에게는 좋은 소식도 나쁜 소식도 아니다, 축하도 경고도 아닌 중립으로 둔다
  const tone: 'GOOD' | 'BAD' | 'NEUTRAL' =
    standing === 'ONLOOKER'
      ? 'NEUTRAL'
      : standing === 'WON' || (standing === 'SELLER' && !unsold)
        ? 'GOOD'
        : 'BAD'

  const TONE_STYLE = {
    GOOD: { icon: Trophy, className: 'bg-price-up/12 text-price-up' },
    BAD: { icon: CircleAlert, className: 'bg-destructive/10 text-destructive' },
    NEUTRAL: { icon: Gavel, className: 'bg-muted text-muted-foreground' },
  }[tone]

  const ToneIcon = TONE_STYLE.icon

  return (
    <section className="flex flex-wrap items-center justify-between gap-4 rounded-xl border p-6">
      <div className="flex items-center gap-4">
        <span
          className={`flex size-12 shrink-0 items-center justify-center rounded-full ${TONE_STYLE.className}`}
          aria-hidden
        >
          <ToneIcon className="size-6" />
        </span>

        <div>
          <h2 className="text-2xl font-bold tracking-tight">{title}</h2>
          <p className="text-muted-foreground mt-1 text-sm">
            {result.myBid ? (
              <>
                내 최고 입찰{' '}
                <strong className="text-foreground">{formatKRW(result.myBid.amount)}</strong>
                {gap !== null && gap > 0 && (
                  <>
                    {' · '}낙찰가와 <strong className="text-foreground">{formatKRW(gap)}</strong>{' '}
                    차이
                  </>
                )}
                {' · '}입찰한 {result.bidderCount}명 중{' '}
                <strong className="text-foreground">{result.myBid.rank}번째</strong>
              </>
            ) : standing === 'SELLER' ? (
              user ? `${user.realName}님이 내놓은 차예요` : '내가 내놓은 차예요'
            ) : (
              // 아무도 안 넣은 경매에 "0명이 참여했어요"는 제목이 이미 한 말이다
              result.bidderCount > 0 && `${result.bidderCount}명이 참여했어요`
            )}
          </p>
        </div>
      </div>

      {/*
        차를 내놓은 사람에게는 추천이 앞뒤가 안 맞는다. 팔렸으면 거래로 가고 안 팔렸으면
        다시 올릴 길만 있으면 된다.
        "이 판매자 다른 차"는 지웠다(#256), 판매자의 경매만 모아 볼 길이 목록에 없다
      */}
      <div className="flex flex-wrap gap-2">
        {standing === 'SELLER' ? (
          unsold ? (
            <Button asChild>
              <Link to="/sell">다시 등록하기</Link>
            </Button>
          ) : (
            <Button asChild>
              <Link to="/mypage/deals">거래 진행하기</Link>
            </Button>
          )
        ) : (
          <>
            <Button variant={standing === 'WON' ? 'outline' : 'default'} asChild>
              <Link to={similarPath}>비슷한 경매 보기</Link>
            </Button>
            {standing === 'WON' && (
              <Button asChild>
                <Link to="/mypage/deals">거래 진행하기</Link>
              </Button>
            )}
          </>
        )}
      </div>
    </section>
  )
}

function CurveCard({ result }: { result: RoomResultView }) {
  const shape = curveShapeOf(result)

  // 내 최고가와 낙찰가가 붙었을 때 표시를 어떻게 합칠지는 곡선이 자기 좌표를 보고 정한다
  const mineWon = viewerStandingOf(result) === 'WON'

  return (
    <section className="rounded-xl border p-6">
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="font-semibold">가격이 오른 과정</h3>

        <div className="text-muted-foreground flex items-center gap-3 text-sm">
          {/* 빨강이 나라는 뜻은 입찰한 사람에게만 필요하다, 안 넣었으면 곡선에 빨강이 없다 */}
          {result.myBid && (
            <span className="flex items-center gap-1.5">
              <span className="bg-destructive size-2 rounded-full" aria-hidden />
              내 입찰
            </span>
          )}
          <span>
            {minutesBetween(result.startAt, result.endAt)}분 동안 {result.bidCount}번
          </span>
        </div>
      </div>

      {shape ? (
        <PriceCurve
          shape={shape}
          startAt={result.startAt}
          endAt={result.endAt}
          myAmount={result.myBid?.amount ?? null}
          mineWon={mineWon}
        />
      ) : (
        <p className="text-muted-foreground py-12 text-center text-sm">
          입찰이 없어 오른 과정이 없어요.
        </p>
      )}
    </section>
  )
}

function WinningCard({ result }: { result: RoomResultView }) {
  const { user } = useAuth()

  if (result.winningPrice === null || result.winner === null) {
    return (
      <section className="rounded-xl border p-6 text-center">
        <p className="text-muted-foreground text-sm">최종 결과</p>
        <p className="mt-1 text-4xl font-bold tracking-tight">유찰</p>
        <p className="text-muted-foreground mt-2 text-sm">
          시작가 {formatKRW(result.startPrice)}
        </p>
      </section>
    )
  }

  // 서버는 낙찰자 이름을 누구에게나 마스킹해서 내려준다. 본인에게만은 실명이 자연스러운데,
  // 그 이름은 서버에 다시 물을 것 없이 내 세션이 이미 들고 있다
  const winnerName = result.winner.mine && user ? user.realName : result.winner.name
  const rise = Math.round(((result.winningPrice - result.startPrice) / result.startPrice) * 1000) / 10

  return (
    <section className="rounded-xl border p-6 text-center">
      <p className="text-muted-foreground text-sm">최종 낙찰가</p>
      <p className="tabular text-price-up mt-1 text-4xl font-bold tracking-tight">
        {formatKRW(result.winningPrice)}
      </p>
      <p className="text-muted-foreground mt-2 text-sm">
        낙찰자 {winnerName}
        {result.winner.mine && <span className="text-foreground font-medium"> (나)</span>}
        {' · '}시작가 대비 +{rise}%
      </p>
    </section>
  )
}

function FactGrid({ result }: { result: RoomResultView }) {
  const facts = [
    { label: '입찰', value: `${result.bidCount}건`, icon: Gavel },
    { label: '참여자', value: `${result.bidderCount}명` },
    { label: '연장', value: `${result.extensionCount}회` },
    { label: '마감', value: formatClock(result.endAt) },
  ]

  return (
    // 남는 높이를 이 표가 가져간다, 네 칸이 같은 키로 늘어야 2×2 로 읽힌다
    <dl className="bg-border grid flex-1 auto-rows-fr grid-cols-2 gap-px overflow-hidden rounded-xl border">
      {facts.map((fact) => (
        <div key={fact.label} className="bg-card flex flex-col justify-center px-5 py-4">
          <dt className="text-muted-foreground text-sm">{fact.label}</dt>
          <dd className="tabular mt-1 text-2xl font-semibold">{fact.value}</dd>
        </div>
      ))}
    </dl>
  )
}

function ResultNotice({ title, description }: { title: string; description: string }) {
  return (
    <main aria-label="경매 결과" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title={title}
        description={description}
        action={
          <Button asChild variant="outline">
            <Link to="/auctions">다른 경매 보기</Link>
          </Button>
        }
      />
    </main>
  )
}

function SignInNotice() {
  const location = useLocation()

  return (
    <main aria-label="경매 결과" className="mx-auto max-w-3xl px-6 py-24">
      <EmptyState
        title="로그인이 필요해요"
        description="내가 얼마에 입찰했고 몇 번째였는지는 로그인해야 보입니다."
        action={
          <Button asChild>
            <Link
              to="/login"
              replace
              state={{ returnTo: { pathname: location.pathname, state: location.state } }}
            >
              로그인
            </Link>
          </Button>
        }
      />
    </main>
  )
}

function minutesBetween(from: string, to: string): number {
  return Math.round((new Date(to).getTime() - new Date(from).getTime()) / 60_000)
}
