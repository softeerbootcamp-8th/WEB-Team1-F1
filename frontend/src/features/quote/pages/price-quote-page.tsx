import { useNavigate } from 'react-router-dom'
import { Search } from 'lucide-react'
import { toast } from 'sonner'

import { getErrorMessage } from '@/lib/axios'
import { estimateQuote } from '@/features/quote/api'
import {
  VehicleOwnerForm,
  type VehicleOwnerValues,
} from '@/features/vehicle/components/vehicle-owner-form'

export function PriceQuotePage() {
  const navigate = useNavigate()

  const showQuote = async (values: VehicleOwnerValues) => {
    try {
      const quote = await estimateQuote(values.plateNumber, values.ownerName)
      navigate('/quote/result', { state: { quote, ownerName: values.ownerName } })
    } catch (error) {
      toast.error(getErrorMessage(error, '시세 조회에 실패했습니다'))
    }
  }

  return (
    <main className="mx-auto max-w-5xl px-6 py-14" aria-label="내 차 시세 조회">
      <header className="max-w-2xl">
        <p className="text-muted-foreground text-sm tracking-[0.18em] uppercase">
          Price Check
        </p>
        <h1 className="mt-4 text-4xl font-semibold md:text-5xl">
          내 차 예상 시세를 조회해보세요!
        </h1>
        <p className="text-muted-foreground mt-4 leading-7">
          차량 소유자 이름과 번호판만 입력하면 예상 시세를 알려드립니다.
        </p>
      </header>

      <div className="mt-12 rounded-2xl border p-7">
        <VehicleOwnerForm
          actionLabel="시세 보기"
          actionIcon={Search}
          onSubmit={showQuote}
        />
      </div>
    </main>
  )
}
