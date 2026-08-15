import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { LoaderCircle, Search } from 'lucide-react'
import { toast } from 'sonner'

import quoteHero from '@/assets/quote-hero-v2.jpg'
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
    <main
      className="relative h-[calc(100svh-var(--spacing-header))] overflow-hidden bg-[#241a14]"
      aria-label="내 차 시세 조회"
    >
      {/* 화면 전체 배경이라 사진은 장식이 된다, alt 를 비운다 */}
      <img
        src={quoteHero}
        alt=""
        aria-hidden
        fetchPriority="high"
        className="absolute inset-0 size-full object-cover object-[center_58%]"
      />
      {/* 위는 흰 제목, 아래는 흰 카드가 놓이므로 양끝을 눌러 대비를 만든다 */}
      <div
        className="absolute inset-0 bg-linear-to-b from-black/45 via-black/10 to-black/40"
        aria-hidden
      />

      <div className="relative mx-auto flex h-full max-w-5xl flex-col px-6 pt-14 pb-[90px]">
        <header>
          <h1 className="text-3xl font-semibold text-white md:text-4xl">
            {vehicle
              ? '주행거리 입력'
              : '내 차량 시세 정보를 확인해 보세요'}
          </h1>
        </header>

        {vehicle && owner ? (
          <section className="bg-background mx-auto mt-auto w-full rounded-2xl border p-6 shadow-[0_24px_60px_rgba(15,23,42,0.45)] md:p-8">
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
          // 흰 카드는 제목과 같은 열을 쓴다, 좌우 끝이 제목 블록에 맞도록 컨테이너 폭을 그대로 채운다.
          // 입력 열은 465px 로 묶어 두므로 넓어진 만큼은 흰 여백이 된다
          <div className="bg-background mt-auto w-full rounded-2xl border px-[54px] py-6 shadow-[0_24px_60px_rgba(15,23,42,0.45)] md:px-[62px] md:py-8">
            <VehicleOwnerForm
              actionLabel="차량 정보 확인"
              actionIcon={Search}
              initialValues={owner ?? undefined}
              isSubmitting={isLookingUp}
              onSubmit={findVehicle}
              className="max-w-[465px]"
            />
          </div>
        )}
      </div>
    </main>
  )
}
