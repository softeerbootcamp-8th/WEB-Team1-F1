import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LoaderCircle, Search } from 'lucide-react'
import { toast } from 'sonner'

import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { estimateQuote } from '@/features/quote/api'
import { lookupVehicle } from '@/features/vehicle/api'
import {
  VehicleOwnerForm,
  type VehicleOwnerValues,
} from '@/features/vehicle/components/vehicle-owner-form'
import { VehicleSummary } from '@/features/vehicle/components/vehicle-summary'
import type { VehicleLookupResponse } from '@/features/vehicle/types'
import { getErrorMessage } from '@/lib/axios'
import { formatNumericInput, parseNumericInput } from '@/lib/input-format'

export function PriceQuotePage() {
  const navigate = useNavigate()
  const [owner, setOwner] = useState<VehicleOwnerValues | null>(null)
  const [vehicle, setVehicle] = useState<VehicleLookupResponse | null>(null)
  const [mileage, setMileage] = useState('')
  const [isLookingUp, setIsLookingUp] = useState(false)
  const [isEstimating, setIsEstimating] = useState(false)

  const mileageNumber = parseNumericInput(mileage)
  const isMileageValid =
    mileage.length > 0 &&
    Number.isInteger(mileageNumber) &&
    mileageNumber >= 0 &&
    mileageNumber <= 999_999

  const findVehicle = async (values: VehicleOwnerValues) => {
    setIsLookingUp(true)
    try {
      const response = await lookupVehicle(values)
      setOwner(values)
      setVehicle(response)
      setMileage('')
    } catch (error) {
      toast.error(getErrorMessage(error, '차량 정보를 확인하지 못했습니다'))
    } finally {
      setIsLookingUp(false)
    }
  }

  const showQuote = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!owner || !isMileageValid) return

    setIsEstimating(true)
    try {
      const quote = await estimateQuote(
        owner.plateNumber,
        owner.ownerName,
        mileageNumber,
      )
      navigate('/quote/result', { state: { quote, ownerName: owner.ownerName } })
    } catch (error) {
      toast.error(getErrorMessage(error, '시세 조회에 실패했습니다'))
    } finally {
      setIsEstimating(false)
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 시세 조회">
      <header>
        <h1 className="text-3xl font-semibold md:text-4xl lg:text-5xl lg:whitespace-nowrap">
          {vehicle
            ? '주행거리 입력'
            : '내 차 정보를 확인해보세요!'}
        </h1>
        <p className="text-muted-foreground mt-3 text-lg leading-8">
          {vehicle
            ? '확인된 차량에 현재 주행거리를 반영해 예상 시세를 계산합니다.'
            : '차량 소유자 이름과 번호판으로 차량 정보를 먼저 확인합니다.'}
        </p>
      </header>

      {vehicle && owner ? (
        <section className="mt-12 rounded-2xl border p-7 md:p-8">
          <VehicleSummary
            vehicle={vehicle}
            balancedRows
            childrenClassName="grid h-full grid-rows-2"
          >
            <form className="contents" onSubmit={showQuote}>
              <div className="flex items-center gap-4 self-start">
                <Label htmlFor="quote-mileage" className="shrink-0">
                  현재 주행거리
                </Label>
                <div className="relative flex-1">
                  <Input
                    id="quote-mileage"
                    type="text"
                    inputMode="numeric"
                    value={mileage}
                    onChange={(event) => setMileage(formatNumericInput(event.target.value, 6))}
                    placeholder="45,000"
                    maxLength={7}
                    className="pr-12 tabular"
                    required
                  />
                  <span className="text-muted-foreground pointer-events-none absolute top-1/2 right-3 -translate-y-1/2 text-sm">
                    km
                  </span>
                </div>
              </div>

              <Button
                type="submit"
                size="lg"
                className="w-full self-end"
                disabled={isEstimating || !isMileageValid}
              >
                {isEstimating ? (
                  <LoaderCircle className="size-4 animate-spin" />
                ) : (
                  <Search className="size-4" />
                )}
                예상 시세 조회하기
              </Button>
            </form>
          </VehicleSummary>
        </section>
      ) : (
        <div className="mx-auto mt-12 w-full max-w-5xl rounded-2xl border p-7 md:px-12 md:py-10 lg:px-16">
          <VehicleOwnerForm
            actionLabel="차량 정보 확인"
            actionIcon={Search}
            initialValues={owner ?? undefined}
            isSubmitting={isLookingUp}
            onSubmit={findVehicle}
          />
        </div>
      )}
    </main>
  )
}
