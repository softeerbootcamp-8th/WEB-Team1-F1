import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight } from 'lucide-react'
import { toast } from 'sonner'

import { lookupVehicle } from '@/features/vehicle/api'
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

  const reserveVisitQuote = async (values: VehicleOwnerValues) => {
    const existingVehicle =
      prefill?.ownerName === values.ownerName &&
      prefill.plateNumber === values.plateNumber &&
      prefill.vehicle?.plateNumber === values.plateNumber
        ? prefill.vehicle
        : null

    if (existingVehicle) {
      navigate('/sell/evaluator', {
        state: { ...values, vehicle: existingVehicle },
      })
      return
    }

    setIsLookingUp(true)
    try {
      const vehicle = await lookupVehicle(values)
      navigate('/sell/evaluator', { state: { ...values, vehicle } })
    } catch (error) {
      toast.error(getErrorMessage(error, '차량 정보를 확인하지 못했습니다'))
    } finally {
      setIsLookingUp(false)
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 팔기">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Sell with RACE
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          차량 정보로 판매 준비를 시작하세요.
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          차량 정보를 확인한 뒤 평가사 방문견적 신청으로 이어집니다.
        </p>
      </header>

      <div className="mt-12 rounded-2xl border p-7">
        <VehicleOwnerForm
          actionLabel="방문견적 예약하기"
          actionIcon={ArrowRight}
          initialValues={prefill ?? undefined}
          isSubmitting={isLookingUp}
          onSubmit={reserveVisitQuote}
        />
      </div>
    </main>
  )
}
