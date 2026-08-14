import { useState } from 'react'
import { isAxiosError } from 'axios'
import { useLocation, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'

import sellHero from '@/assets/sell-hero-wide-v2.jpg'
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
    <main
      className="mx-auto h-[calc(100svh-var(--spacing-header))] max-w-5xl overflow-hidden px-6 pt-14 pb-6"
      aria-label="내 차 팔기"
    >
      <header>
        <h1 className="text-3xl font-semibold md:text-4xl">
          차량 정보로 판매 준비를 시작하세요.
        </h1>
      </header>

      <div className="relative mt-5 h-[clamp(8rem,20svh,13rem)] overflow-hidden rounded-2xl bg-[#101820]">
        <img
          src={sellHero}
          alt="사막을 달리는 차량"
          fetchPriority="high"
          className="size-full object-cover object-[center_66%]"
        />
        <div
          className="pointer-events-none absolute inset-0 bg-linear-to-r from-black/15 via-transparent to-transparent"
          aria-hidden
        />
      </div>

      <section
        aria-label="판매 차량 확인"
        className="mx-auto mt-5 w-full max-w-3xl rounded-2xl border border-black/8 bg-white p-6 shadow-[0_18px_50px_rgba(15,23,42,0.1)] md:px-12 lg:px-16"
      >
        <VehicleOwnerForm
          actionLabel="방문견적 예약하기"
          initialValues={prefill ?? undefined}
          isSubmitting={isLookingUp}
          onSubmit={reserveVisitQuote}
        />
        {duplicateMessage && (
          <p className="text-destructive mx-auto mt-4 max-w-sm text-sm" role="alert">
            {duplicateMessage}
          </p>
        )}
      </section>
    </main>
  )
}
