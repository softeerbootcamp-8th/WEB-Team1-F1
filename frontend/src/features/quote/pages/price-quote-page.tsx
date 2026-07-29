import { useState } from 'react'
import { Search } from 'lucide-react'

import { formatKRW } from '@/lib/format'
import {
  VehicleOwnerForm,
  type VehicleOwnerValues,
} from '@/features/vehicle/components/vehicle-owner-form'

export function PriceQuotePage() {
  const [result, setResult] = useState<
    (VehicleOwnerValues & { estimatedPrice: number }) | null
  >(null)

  const showQuote = (values: VehicleOwnerValues) => {
    const digits = Number(values.plateNumber.replace(/\D/g, '').slice(-4)) || 0
    const estimatedPrice = 19_000_000 + (digits % 40) * 250_000
    setResult({ ...values, estimatedPrice })
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 시세 조회">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Price Check
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          로그인 없이 확인하는
          <br />
          내 차 예상 시세
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          차량 소유자 이름과 번호판만 입력하면 예상 시세를 알려드립니다.
        </p>
      </header>

      <div className="mt-12 grid gap-8 lg:grid-cols-[1fr_0.8fr]">
        <div className="rounded-2xl border p-7">
          <VehicleOwnerForm
            actionLabel="시세 보기"
            actionIcon={Search}
            onSubmit={showQuote}
          />
        </div>

        <section className="bg-foreground text-background flex min-h-72 flex-col justify-center rounded-2xl p-8">
          <p className="text-background/55 text-sm">예상 시세</p>
          {result ? (
            <>
              <p className="tabular mt-3 text-4xl font-semibold">
                {formatKRW(result.estimatedPrice)}
              </p>
              <p className="text-background/55 mt-3 text-sm">
                {result.ownerName} · {result.plateNumber}
              </p>
              <p className="text-background/55 mt-2 text-xs">
                실제 경매가는 평가 결과에 따라 달라질 수 있습니다.
              </p>
            </>
          ) : (
            <p className="text-background/55 mt-4 text-sm leading-6">
              이름과 번호판을 입력하면 이곳에 예상 시세가 표시됩니다.
            </p>
          )}
        </section>
      </div>
    </main>
  )
}
