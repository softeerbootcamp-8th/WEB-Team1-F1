import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowLeft, LoaderCircle, Search } from 'lucide-react'
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
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Price Check
        </p>
        <h1 className="mt-4 text-3xl font-semibold md:text-4xl lg:text-5xl lg:whitespace-nowrap">
          {vehicle
            ? '현재 주행거리를 알려주세요.'
            : '내 차 정보를 확인해보세요!'}
        </h1>
        <p className="text-muted-foreground mt-4 text-lg leading-8">
          {vehicle
            ? '확인된 차량에 현재 주행거리를 반영해 예상 시세를 계산합니다.'
            : '차량 소유자 이름과 번호판으로 차량 정보를 먼저 확인합니다.'}
        </p>
      </header>

      {vehicle && owner ? (
        <div className="mt-12 grid gap-8 lg:grid-cols-[1.2fr_0.8fr]">
          <aside className="rounded-2xl border p-7 md:p-8">
            <VehicleSummary vehicle={vehicle} />
          </aside>

          <section className="min-w-0 self-start rounded-2xl border p-7 md:p-8">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="-ml-2"
              onClick={() => setVehicle(null)}
            >
              <ArrowLeft className="size-4" />
              차량 정보 수정
            </Button>

            <form className="mt-8 space-y-6" onSubmit={showQuote}>
              <div className="space-y-2">
                <Label htmlFor="quote-mileage">현재 주행거리</Label>
                <div className="relative">
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
                <p className="text-muted-foreground text-xs">
                  현재 계기판에 표시된 주행거리를 입력해 주세요.
                </p>
              </div>

              <Button
                type="submit"
                size="lg"
                className="w-full"
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
          </section>
        </div>
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
