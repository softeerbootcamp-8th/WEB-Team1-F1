import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, ArrowRight, Check, LoaderCircle, UserRoundSearch } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { SchedulePicker } from '@/features/sell/components/schedule-picker'
import type { VehicleOwnerValues } from '@/features/vehicle/components/vehicle-owner-form'

const VISIT_HOURS = Array.from({ length: 11 }, (_, i) => 10 + i) // 10~20시

type Phase = 'scheduling' | 'confirmed' | 'visiting' | 'inspecting' | 'done'

const PHASE_ORDER: Phase[] = ['scheduling', 'confirmed', 'visiting', 'inspecting', 'done']

function formatVisitAt(date: Date) {
  return date.toLocaleString('ko-KR', {
    month: 'long',
    day: 'numeric',
    weekday: 'short',
    hour: 'numeric',
    minute: '2-digit',
  })
}

export function EvaluatorConnectionPage() {
  const { state } = useLocation()
  const navigate = useNavigate()
  const vehicle = state as VehicleOwnerValues | null

  const [visitAt, setVisitAt] = useState<Date | null>(null)
  const [phase, setPhase] = useState<Phase>('scheduling')

  useEffect(() => {
    if (phase === 'confirmed') {
      const id = window.setTimeout(() => setPhase('visiting'), 2000)
      return () => window.clearTimeout(id)
    }
    if (phase === 'visiting') {
      const id = window.setTimeout(() => setPhase('inspecting'), 2500)
      return () => window.clearTimeout(id)
    }
    if (phase === 'inspecting') {
      const id = window.setTimeout(() => setPhase('done'), 2500)
      return () => window.clearTimeout(id)
    }
  }, [phase])

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

  const stepIndex = PHASE_ORDER.indexOf(phase)

  const goToAuctionPost = () => {
    navigate('/sell/auction-post', { state: vehicle })
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="평가사 연결">
      <Button asChild variant="ghost" size="sm" className="-ml-2">
        <Link to="/sell">
          <ArrowLeft className="size-4" />
          차량 정보 수정
        </Link>
      </Button>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_0.8fr]">
        <section className="min-w-0 rounded-2xl border p-7 md:p-10">
          <div className="bg-primary/10 text-primary flex size-12 items-center justify-center rounded-full">
            <UserRoundSearch className="size-6" />
          </div>
          <p className="text-muted-foreground mt-8 text-sm">평가사 연결</p>

          {phase === 'scheduling' ? (
            <>
              <h1 className="mt-2 text-3xl font-semibold">
                평가사 방문 일정을
                <br />
                선택해 주세요.
              </h1>
              <div className="mt-8">
                <SchedulePicker
                  hours={VISIT_HOURS}
                  minDateTime={new Date()}
                  onSelect={setVisitAt}
                />
              </div>
              <Button
                size="lg"
                className="mt-8 w-full"
                disabled={!visitAt}
                onClick={() => setPhase('confirmed')}
              >
                평가사 방문 일정 확정
              </Button>
            </>
          ) : (
            <>
              <h1 className="mt-2 text-3xl font-semibold">
                {phase === 'done' ? '차량 검사가 완료됐어요.' : '평가사를 연결하고 있습니다.'}
              </h1>
              {visitAt && (
                <p className="text-muted-foreground mt-2 text-sm" aria-live="polite">
                  방문 예정: {formatVisitAt(visitAt)}
                </p>
              )}
            </>
          )}
        </section>

        <div className="rounded-2xl border p-7 md:p-8">
          <div className="bg-muted/50 rounded-xl p-5">
            <p className="font-medium">{vehicle.ownerName}</p>
            <p className="text-muted-foreground tabular mt-1 text-sm">
              {vehicle.plateNumber}
            </p>
          </div>

          <ol className="mt-8 space-y-4 text-sm" aria-live="polite">
            <li className="flex items-center gap-3">
              <Check className="text-success size-4" />
              차량 소유 정보 확인 완료
            </li>
            <li
              className={
                stepIndex >= 1
                  ? 'flex items-center gap-3'
                  : 'text-muted-foreground flex items-center gap-3'
              }
            >
              {stepIndex >= 1 ? (
                <Check className="text-success size-4" />
              ) : (
                <span className="size-4 rounded-full border" />
              )}
              방문 일정 확정
            </li>
            <li
              className={
                stepIndex >= 2
                  ? 'flex items-center gap-3'
                  : 'text-muted-foreground flex items-center gap-3'
              }
            >
              {stepIndex > 2 ? (
                <Check className="text-success size-4" />
              ) : stepIndex === 2 ? (
                <LoaderCircle className="text-primary size-4 animate-spin" />
              ) : (
                <span className="size-4 rounded-full border" />
              )}
              평가사 방문 중
            </li>
            <li
              className={
                stepIndex >= 3
                  ? 'flex items-center gap-3'
                  : 'text-muted-foreground flex items-center gap-3'
              }
            >
              {stepIndex > 3 ? (
                <Check className="text-success size-4" />
              ) : stepIndex === 3 ? (
                <LoaderCircle className="text-primary size-4 animate-spin" />
              ) : (
                <span className="size-4 rounded-full border" />
              )}
              차량 검사 완료
            </li>
          </ol>

          {phase === 'done' && (
            <Button size="lg" className="mt-8 w-full" onClick={goToAuctionPost}>
              <ArrowRight className="size-4" />
              경매글 등록하러 가기
            </Button>
          )}
        </div>
      </div>
    </main>
  )
}
