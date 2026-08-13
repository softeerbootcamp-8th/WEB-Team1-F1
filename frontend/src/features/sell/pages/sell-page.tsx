import { useState } from 'react'
import { isAxiosError } from 'axios'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import { precheckVisitQuote } from '@/features/sell/api'
import {
  VehicleOwnerForm,
  type VehicleOwnerValues,
} from '@/features/vehicle/components/vehicle-owner-form'
import type { VehicleLookupResponse } from '@/features/vehicle/types'
import { getErrorMessage } from '@/lib/axios'

interface SellPageState extends Partial<VehicleOwnerValues> {
  vehicle?: VehicleLookupResponse
}

export function SellPage() {
  const navigate = useNavigate()
  const { state } = useLocation()
  const prefill = state as SellPageState | null
  const [isLookingUp, setIsLookingUp] = useState(false)
  const [duplicateMessage, setDuplicateMessage] = useState<string | null>(null)

  const reserveVisitQuote = async (values: VehicleOwnerValues) => {
    setDuplicateMessage(null)
    setIsLookingUp(true)
    try {
      const { vehicle, hasInProgressVisitQuote } =
        await precheckVisitQuote(values)

      if (hasInProgressVisitQuote) {
        const message =
          '이미 진행 중인 방문견적이 있는 차량입니다. 기존 신청이 종료된 후 다시 신청해 주세요.'
        setDuplicateMessage(message)
        toast.error(message)
        return
      }

      navigate('/sell/evaluator', { state: { ...values, vehicle } })
    } catch (error) {
      if (isAxiosError(error) && error.response?.status === 401) {
        toast.error('방문견적을 확인하려면 로그인이 필요합니다')
        navigate('/login', {
          state: {
            returnTo: {
              pathname: '/sell',
              state: values,
            },
          },
        })
        return
      }

      toast.error(getErrorMessage(error, '차량 정보를 확인하지 못했습니다'))
    } finally {
      setIsLookingUp(false)
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 팔기">
      <header>
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Sell with RACE
        </p>
        <h1 className="mt-4 text-3xl font-semibold md:text-4xl lg:text-5xl lg:whitespace-nowrap">
          차량 정보로 판매 준비를 시작하세요.
        </h1>
        <p className="text-muted-foreground mt-4 text-lg leading-8">
          차량 정보를 확인한 뒤 평가사 방문견적 신청으로 이어집니다.
        </p>
      </header>

      <div className="mx-auto mt-12 w-full max-w-5xl rounded-2xl border p-7 md:px-12 md:py-10 lg:px-16">
        <VehicleOwnerForm
          actionLabel="방문견적 예약하기"
          initialValues={prefill ?? undefined}
          isSubmitting={isLookingUp}
          onSubmit={reserveVisitQuote}
        />
        {duplicateMessage && (
          <p className="text-destructive mt-4 text-sm" role="alert">
            {duplicateMessage}
          </p>
        )}
      </div>
    </main>
  )
}
