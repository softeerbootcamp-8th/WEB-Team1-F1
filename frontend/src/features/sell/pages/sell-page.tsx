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
      className="relative h-[calc(100svh-var(--spacing-header))] overflow-hidden bg-[#101820]"
      aria-label="내 차 팔기"
    >
      {/* 화면 전체 배경이라 사진은 장식이 된다, alt 를 비운다 */}
      <img
        src={sellHero}
        alt=""
        aria-hidden
        fetchPriority="high"
        className="absolute inset-0 size-full object-cover object-[center_66%]"
      />
      {/* 위는 흰 제목, 아래는 흰 카드가 놓이므로 양끝을 눌러 대비를 만든다 */}
      <div
        className="absolute inset-0 bg-linear-to-b from-black/45 via-black/10 to-black/40"
        aria-hidden
      />

      <div className="relative mx-auto flex h-full max-w-5xl flex-col px-6 pt-14 pb-[90px]">
        <header>
          <h1 className="text-3xl font-semibold text-white md:text-4xl">
            차량 정보로 판매 준비를 시작하세요.
          </h1>
        </header>

        {/* 흰 카드는 제목과 같은 열을 쓴다, 좌우 끝이 제목 블록에 맞도록 컨테이너 폭을 그대로 채운다.
            입력 열은 465px 로 묶어 두므로 넓어진 만큼은 흰 여백이 된다 */}
        <section
          aria-label="판매 차량 확인"
          className="mt-auto w-full rounded-2xl border border-black/8 bg-white px-[54px] py-6 shadow-[0_24px_60px_rgba(15,23,42,0.45)] md:px-[62px] md:py-8"
        >
          <VehicleOwnerForm
            actionLabel="방문견적 예약하기"
            initialValues={prefill ?? undefined}
            isSubmitting={isLookingUp}
            onSubmit={reserveVisitQuote}
            className="max-w-[465px]"
          />
          {duplicateMessage && (
            <p className="text-destructive mx-auto mt-4 max-w-[465px] text-sm" role="alert">
              {duplicateMessage}
            </p>
          )}
        </section>
      </div>
    </main>
  )
}
