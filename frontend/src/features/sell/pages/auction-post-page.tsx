import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowLeft, Gavel, LoaderCircle } from 'lucide-react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/features/auth/auth-context'
import { fetchEvaluationDetail } from '@/features/evaluations/api'
import type { FuelType, Manufacturer, Transmission } from '@/features/quote/types'
import { MANUFACTURER_LABEL } from '@/features/quote/types'
import { createAuction } from '@/features/sell/api'
import { SchedulePicker } from '@/features/sell/components/schedule-picker'
import { getErrorMessage } from '@/lib/axios'
import { formatKRW } from '@/lib/format'

const AUCTION_START_HOURS = Array.from({ length: 15 }, (_, i) => 10 + i) // 10시~24시(자정)
const MIN_LEAD_TIME_MS = 60 * 60 * 1000

// 시작가 입력 상한(만원 단위 6자리 = 999,999만원 ≈ 100억). AuctionEditDialog와 같은 값이다.
// 서버는 상한이 없어(@PositiveOrZero) 자릿수 실수를 걸러 줄 곳이 화면뿐이다.
const MAX_PRICE_DIGITS = 6

interface EvaluatedVehicle {
  vehicleId: number
  estimatedPrice: number
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  fuelType: FuelType
  transmission: Transmission
  mileage: number | null
  imageUrls: string[]
}

function formatStartAt(date: Date) {
  return date.toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}

/** Date를 UTC로 바꾸지 않고 백엔드 LocalDateTime 형식으로 직렬화한다. */
function formatLocalDateTime(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  const second = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`
}

export function AuctionPostPage() {
  const { user } = useAuth()
  const [searchParams] = useSearchParams()
  const evaluationId = Number(searchParams.get('evaluationId'))
  const hasEvaluationId = Number.isInteger(evaluationId) && evaluationId > 0

  const { data, isLoading } = useQuery({
    queryKey: ['evaluations', 'detail', user?.id, evaluationId],
    queryFn: () => fetchEvaluationDetail(evaluationId),
    enabled: user != null && hasEvaluationId,
  })

  if (user != null && hasEvaluationId && isLoading) {
    return (
      <main className="flex min-h-[60vh] items-center justify-center">
        <LoaderCircle
          className="size-7 animate-spin"
          aria-label="차량 정보 불러오는 중"
        />
      </main>
    )
  }

  const vehicle =
    data?.status === 'APPROVED' && data.estimatedPrice !== null
      ? { ...data, estimatedPrice: data.estimatedPrice }
      : null

  if (!vehicle) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="진단이 완료된 차량 정보가 필요합니다"
          description="마이페이지에서 방문견적 결과를 확인한 뒤 경매 등록을 진행해 주세요."
          action={
            <Button asChild>
              <Link to="/mypage">마이페이지로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return <AuctionPostForm key={evaluationId} vehicle={vehicle} />
}

function AuctionPostForm({ vehicle }: { vehicle: EvaluatedVehicle }) {
  const navigate = useNavigate()
  const [startAt, setStartAt] = useState<Date | null>(null)
  // 시작가는 만원 단위로만 받는다. AuctionEditDialog와 같은 방식이다 — 원 단위로 받아 검증하는
  // 대신 입력 단위를 만원으로 두면 1,232만 4,341원 같은 시작가가 애초에 만들어지지 않는다.
  // 진단 시세는 원 단위라 초기값에서 만원 미만을 버린다. 올리면 시세보다 높은 시작가가 되어
  // 첫 입찰이 붙지 않으므로 방향은 버림이다.
  const [priceManwon, setPriceManwon] = useState(
    String(Math.floor(vehicle.estimatedPrice / 10000)),
  )
  const startPriceNumber = Number(priceManwon || 0) * 10000
  // 자릿수도 함께 본다. onChange 는 6자리로 자르지만 초기값은 그 경로를 타지 않는다 —
  // 진단 시세는 평가사가 직접 부르는 값이라(Vehicle.completeDiagnosis) 오타로 큰 수가 들어올 수
  // 있고, 서버에 상한이 없어(@PositiveOrZero) 그대로 등록되면 막을 곳이 없다.
  // 값을 지우지는 않는다. 서버가 준 시세를 보여 줘야 얼마를 잘못 받았는지 알 수 있다.
  const isStartPriceValid =
    priceManwon !== '' &&
    priceManwon.length <= MAX_PRICE_DIGITS &&
    startPriceNumber > 0

  const mutation = useMutation({
    mutationFn: () => {
      if (!startAt || !isStartPriceValid) {
        throw new Error('차량 정보와 경매 시작가·시각을 확인해 주세요.')
      }
      return createAuction({
        vehicleId: vehicle.vehicleId,
        startPrice: startPriceNumber,
        startAt: formatLocalDateTime(startAt),
      })
    },
    onSuccess: (result) => {
      toast.success('경매가 등록되었습니다')
      navigate('/sell/result', { replace: true, state: result })
    },
    onError: (error) => {
      toast.error(
        error instanceof Error && !('response' in error)
          ? error.message
          : getErrorMessage(error, '경매를 등록하지 못했습니다'),
      )
    },
  })

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="경매글 등록">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/mypage">
          <ArrowLeft className="size-4" />
          마이페이지로
        </Link>
      </Button>

      <header className="mt-6 max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Register Auction
        </p>
        <h1 className="mt-3 text-2xl font-semibold md:text-3xl">
          경매 시작 시간을 정해주세요.
        </h1>
        <p className="text-muted-foreground mt-3 text-sm leading-6">
          지금부터 1시간 뒤부터, 오전 10시에서 밤 12시 사이로 시작 시간을 선택할 수 있어요.
        </p>
      </header>

      <div className="mt-12 grid gap-8 lg:grid-cols-[1fr_0.8fr]">
        <div className="min-w-0 rounded-2xl border p-7">
          <SchedulePicker
            hours={AUCTION_START_HOURS}
            minDateTime={new Date(Date.now() + MIN_LEAD_TIME_MS)}
            onSelect={setStartAt}
          />
        </div>

        <section className="bg-foreground text-background flex min-h-72 flex-col justify-between rounded-2xl p-8">
          <div>
            <p className="text-background/55 text-sm">경매 차량</p>
            <p className="mt-3 text-3xl font-semibold">
              {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
            </p>
            <p className="text-background/55 tabular mt-1 text-base">
              {vehicle.plateNumber}
            </p>
            <p className="text-background/55 mt-7 text-sm">경매 시작 시각</p>
            <p className="tabular mt-1 text-xl font-semibold">
              {startAt ? formatStartAt(startAt) : '아직 선택하지 않았어요'}
            </p>
            {/* 진단 시세는 시작가를 정하는 기준값이라 입력 아래 보조 문구가 아니라
                시작 시각과 같은 층위로 올린다. 입력하는 값과 비교하며 볼 수 있어야 한다. */}
            <p className="text-background/55 mt-7 text-sm">진단 시세</p>
            <p className="tabular mt-1 text-xl font-semibold">
              {formatKRW(vehicle.estimatedPrice)}
            </p>
            <div className="mt-7 space-y-2">
              <Label
                htmlFor="auction-start-price"
                className="text-background/55 text-sm"
              >
                경매 시작가
              </Label>
              <div className="relative">
                <Input
                  id="auction-start-price"
                  // number가 아니라 text다. 스피너 화살표와 휠 조작을 없애고 천단위 콤마를 찍는다.
                  type="text"
                  inputMode="numeric"
                  autoComplete="off"
                  value={
                    priceManwon === ''
                      ? ''
                      : Number(priceManwon).toLocaleString('ko-KR')
                  }
                  onChange={(event) =>
                    // 숫자만 남기고 앞자리 0을 정리한다. 자릿수를 막지 않으면 "1232만4341원"을
                    // 통째로 붙여넣었을 때 숫자만 이어붙어 1,232억짜리 경매가 만들어진다.
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
                  className="bg-background text-foreground tabular h-12 pr-16 !text-xl font-semibold"
                  aria-invalid={priceManwon.length > 0 && !isStartPriceValid}
                />
                <span className="text-foreground/55 pointer-events-none absolute top-1/2 right-4 -translate-y-1/2 text-base">
                  만원
                </span>
              </div>
              <p className="text-background/55 text-sm">
                {isStartPriceValid
                  ? formatKRW(startPriceNumber)
                  : '시작가를 만원 단위로 입력해 주세요.'}
              </p>
            </div>
          </div>

          <Button
            size="lg"
            className="mt-8 w-full"
            variant="secondary"
            disabled={!startAt || !isStartPriceValid || mutation.isPending}
            onClick={() => mutation.mutate()}
          >
            {mutation.isPending ? (
              <LoaderCircle className="size-4 animate-spin" />
            ) : (
              <Gavel className="size-4" />
            )}
            {mutation.isPending ? '등록 중...' : '경매 등록하기'}
          </Button>
        </section>
      </div>
    </main>
  )
}
