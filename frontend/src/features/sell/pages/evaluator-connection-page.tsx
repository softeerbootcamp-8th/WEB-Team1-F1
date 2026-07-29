import { Link, useLocation } from 'react-router-dom'
import { ArrowLeft, Check, LoaderCircle, UserRoundSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import type { VehicleOwnerValues } from '@/features/vehicle/components/vehicle-owner-form'

export function EvaluatorConnectionPage() {
  const { state } = useLocation()
  const vehicle = state as VehicleOwnerValues | null

  if (!vehicle?.ownerName || !vehicle.plateNumber) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24">
        <EmptyState
          title="차량 정보를 먼저 입력해 주세요"
          description="내 차 팔기에서 이름과 번호판을 입력하면 평가사를 연결할 수 있습니다."
          action={
            <Button asChild>
              <Link to="/sell">내 차 팔기로</Link>
            </Button>
          }
        />
      </main>
    )
  }

  return (
    <main className="mx-auto max-w-3xl px-6 py-14" aria-label="평가사 연결">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/sell">
          <ArrowLeft className="size-4" />
          차량 정보 수정
        </Link>
      </Button>

      <section className="mt-6 rounded-2xl border p-8 md:p-12">
        <div className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-full">
          <UserRoundSearch className="size-6" />
        </div>
        <p className="text-muted-foreground mt-8 text-sm">평가사 연결</p>
        <h1 className="mt-2 text-3xl font-semibold">
          차량에 맞는 평가사를
          <br />
          찾고 있습니다.
        </h1>
        <div className="bg-muted/50 mt-8 rounded-xl p-5">
          <p className="font-medium">{vehicle.ownerName}</p>
          <p className="text-muted-foreground tabular mt-1 text-sm">
            {vehicle.plateNumber}
          </p>
        </div>
        <ol className="mt-8 space-y-4 text-sm">
          <li className="flex items-center gap-3">
            <Check className="text-success size-4" />
            차량 소유 정보 확인 완료
          </li>
          <li className="flex items-center gap-3 font-medium">
            <LoaderCircle className="text-primary size-4 animate-spin" />
            방문 가능한 평가사 확인 중
          </li>
          <li className="text-muted-foreground flex items-center gap-3">
            <span className="size-4 rounded-full border" />
            방문 일정 선택
          </li>
        </ol>
      </section>
    </main>
  )
}
