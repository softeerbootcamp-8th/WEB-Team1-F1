import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, ShieldCheck } from 'lucide-react'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/features/auth/auth-context'
import {
  VehicleOwnerForm,
  type VehicleOwnerValues,
} from '@/features/vehicle/components/vehicle-owner-form'

export function SellPage() {
  const navigate = useNavigate()
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="내 차 팔기">
        <EmptyState
          icon={ShieldCheck}
          title="내 차 팔기는 로그인이 필요합니다"
          description="차량 소유자 확인과 평가사 연결을 위해 먼저 로그인해 주세요."
          action={
            <div className="flex gap-2">
              <Button asChild>
                <Link to="/login">로그인</Link>
              </Button>
              <Button asChild variant="outline">
                <Link to="/quote">비회원 시세 조회</Link>
              </Button>
            </div>
          }
        />
      </main>
    )
  }

  const connectEvaluator = (values: VehicleOwnerValues) => {
    navigate('/sell/evaluator', { state: values })
  }

  return (
    <main aria-label="내 차 팔기">
      <section className="bg-foreground text-background">
        <div className="mx-auto max-w-5xl px-6 py-20 md:py-28">
          <p className="text-background/55 text-sm tracking-[0.2em] uppercase">
            Sell with RACE
          </p>
          <h1 className="mt-4 text-4xl font-semibold md:text-6xl">
            이름과 번호판으로
            <br />
            판매 준비를 시작하세요.
          </h1>
          <p className="text-background/65 mt-5 max-w-lg leading-7">
            입력한 차량 정보를 유지한 채 담당 평가사 연결 단계로 이어집니다.
          </p>
        </div>
      </section>

      <section className="mx-auto grid max-w-5xl gap-10 px-6 py-16 lg:grid-cols-[1fr_0.8fr]">
        <div className="rounded-2xl border p-7 md:p-10">
          <VehicleOwnerForm
            actionLabel="평가사 연결하기"
            actionIcon={ArrowRight}
            onSubmit={connectEvaluator}
          />
        </div>

        <ol className="space-y-7 py-3">
          {[
            ['01', '차량 확인', '이름과 번호판으로 소유 차량을 확인합니다.'],
            ['02', '평가사 연결', '차량 진단을 진행할 평가사를 연결합니다.'],
            ['03', '경매 등록', '진단이 끝나면 경매 게시글이 완성됩니다.'],
          ].map(([number, title, description]) => (
            <li key={number} className="flex gap-4">
              <span className="text-muted-foreground tabular text-sm">{number}</span>
              <div>
                <h3 className="font-semibold">{title}</h3>
                <p className="text-muted-foreground mt-1 text-sm">{description}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>
    </main>
  )
}
