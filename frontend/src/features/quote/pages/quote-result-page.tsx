import { Link, useLocation } from 'react-router-dom'
import { Home, Search, Tag } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { formatKRW, formatMileage } from '@/lib/format'
import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
  TRANSMISSION_LABEL,
  type QuoteResult,
} from '@/features/quote/types'

interface QuoteResultState {
  quote: QuoteResult
  ownerName: string
}

export function QuoteResultPage() {
  const { state } = useLocation()
  const data = state as QuoteResultState | null
  const quote = data?.quote ?? null

  if (!quote || !data) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="시세 조회 결과">
        <EmptyState
          icon={Search}
          title="조회한 시세 정보가 없어요"
          description="이름과 번호판, 현재 주행거리를 입력하면 예상 시세를 확인할 수 있어요."
          action={
            <Button asChild>
              <Link to="/quote">시세 조회하러 가기</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="시세 조회 결과">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Price Check
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          예상 시세를 확인해 보세요.
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          실제 경매가는 평가 결과에 따라 달라질 수 있어요.
        </p>
      </header>

      <div className="mt-12 grid gap-8 lg:grid-cols-[1fr_0.8fr]">
        <div className="min-w-0 rounded-2xl border p-7">
          {quote.mainImageUrl && (
            <img
              src={quote.mainImageUrl}
              alt={quote.model}
              className="mb-6 aspect-video w-full rounded-xl object-cover"
            />
          )}
          <p className="text-muted-foreground text-sm">
            {MANUFACTURER_LABEL[quote.manufacturer]}
          </p>
          <p className="mt-1 text-2xl font-semibold">{quote.model}</p>
          <p className="text-muted-foreground tabular mt-1 text-sm">
            {quote.plateNumber}
          </p>

          <dl className="mt-8 grid grid-cols-2 gap-y-5 text-sm">
            <div>
              <dt className="text-muted-foreground">연식</dt>
              <dd className="tabular mt-1 font-medium">{quote.modelYear}년</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">주행거리</dt>
              <dd className="tabular mt-1 font-medium">
                {formatMileage(quote.mileage)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">연료</dt>
              <dd className="mt-1 font-medium">{FUEL_TYPE_LABEL[quote.fuelType]}</dd>
            </div>
            <div>
              <dt className="text-muted-foreground">변속기</dt>
              <dd className="mt-1 font-medium">
                {TRANSMISSION_LABEL[quote.transmission]}
              </dd>
            </div>
          </dl>
        </div>

        <section className="bg-foreground text-background flex min-h-72 flex-col justify-center rounded-2xl p-8">
          <p className="text-background/55 text-sm">예상 시세</p>
          <p className="tabular mt-3 text-4xl font-semibold">
            {formatKRW(quote.estimatedPrice)}
          </p>
          <p className="text-background/55 mt-3 text-sm">{quote.plateNumber}</p>
          <p className="text-background/55 mt-2 text-xs">
            실제 경매가는 평가 결과에 따라 달라질 수 있습니다.
          </p>
        </section>
      </div>

      <div className="mt-10 flex gap-3">
        <Button asChild variant="outline" size="lg" className="flex-1">
          <Link to="/">
            <Home className="size-4" />
            홈으로 돌아가기
          </Link>
        </Button>
        <Button asChild size="lg" className="flex-1">
          <Link
            to="/sell"
            state={{
              ownerName: data.ownerName,
              plateNumber: quote.plateNumber,
              vehicle: {
                plateNumber: quote.plateNumber,
                manufacturer: quote.manufacturer,
                model: quote.model,
                modelYear: quote.modelYear,
                fuelType: quote.fuelType,
                transmission: quote.transmission,
                mainImageUrl: quote.mainImageUrl,
              },
            }}
          >
            <Tag className="size-4" />
            내 차 팔기
          </Link>
        </Button>
      </div>
    </main>
  )
}
