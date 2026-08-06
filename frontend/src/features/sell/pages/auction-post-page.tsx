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
  const [startPrice, setStartPrice] = useState(String(vehicle.estimatedPrice))
  const startPriceNumber = Number(startPrice)
  const isStartPriceValid =
    Number.isSafeInteger(startPriceNumber) && startPriceNumber > 0

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
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          경매 시작 시간을 정해주세요.
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
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
            <p className="mt-3 text-2xl font-semibold">
              {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
            </p>
            <p className="text-background/55 tabular mt-1 text-sm">
              {vehicle.plateNumber}
            </p>
            <p className="text-background/55 mt-6 text-sm">경매 시작 시각</p>
            <p className="tabular mt-1 text-lg font-semibold">
              {startAt ? formatStartAt(startAt) : '아직 선택하지 않았어요'}
            </p>
            <div className="mt-6 space-y-2">
              <Label htmlFor="auction-start-price" className="text-background/55">
                경매 시작가
              </Label>
              <Input
                id="auction-start-price"
                type="number"
                min={1}
                step={1}
                inputMode="numeric"
                value={startPrice}
                onChange={(event) => setStartPrice(event.target.value)}
                className="bg-background text-foreground"
                aria-invalid={startPrice.length > 0 && !isStartPriceValid}
              />
              <p className="text-background/55 text-xs">
                {isStartPriceValid
                  ? `진단 시세 ${formatKRW(vehicle.estimatedPrice)} · 등록 시작가 ${formatKRW(startPriceNumber)}`
                  : '0보다 큰 원 단위 정수를 입력해 주세요.'}
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
