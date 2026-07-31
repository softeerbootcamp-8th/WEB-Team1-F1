import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, ShieldCheck } from 'lucide-react'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { getErrorMessage } from '@/lib/axios'
import { useAuth } from '@/features/auth/auth-context'
import { applySell } from '@/features/sell/api'
import type { VehicleOwnerValues } from '@/features/vehicle/components/vehicle-owner-form'

const PLATE_PATTERN = /^\d{2,3}[가-힣]\d{4}$/

export function SellPage() {
  const navigate = useNavigate()
  const { state } = useLocation()
  const { isAuthenticated } = useAuth()
  const prefill = state as Partial<VehicleOwnerValues> | null
  const [plateNumber, setPlateNumber] = useState(prefill?.plateNumber ?? '')
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (!isAuthenticated) {
    return (
      <main className="mx-auto max-w-3xl px-6 py-24" aria-label="내 차 팔기">
        <EmptyState
          icon={ShieldCheck}
          title="내 차 팔기는 로그인이 필요합니다"
          description="차량 소유자 확인을 위해 먼저 로그인해 주세요."
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

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setIsSubmitting(true)
    try {
      const result = await applySell(plateNumber)
      navigate('/sell/result', { state: result })
    } catch (error) {
      toast.error(getErrorMessage(error, '판매 신청에 실패했습니다'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 팔기">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Sell with RACE
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          내 차를 판매해보세요!
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          번호판만 입력하면 서버가 차량 정보를 조회해 경매를 바로 등록해요. 경매는
          신청 시각으로부터 1시간 뒤에 시작해요.
        </p>
      </header>

      <div className="mt-12 rounded-2xl border p-7">
        <form className="space-y-5" onSubmit={submit}>
          <div className="space-y-2">
            <Label htmlFor="plate-number">차량 번호판</Label>
            <Input
              id="plate-number"
              value={plateNumber}
              onChange={(e) => setPlateNumber(e.target.value)}
              placeholder="12가3456"
              className="h-14 text-lg font-semibold"
              autoComplete="off"
              pattern="^\d{2,3}[가-힣]\d{4}$"
              required
            />
          </div>
          <Button
            type="submit"
            size="lg"
            className="w-full"
            disabled={isSubmitting || !PLATE_PATTERN.test(plateNumber)}
          >
            <ArrowRight className="size-4" />
            경매 등록하기
          </Button>
        </form>
      </div>
    </main>
  )
}
