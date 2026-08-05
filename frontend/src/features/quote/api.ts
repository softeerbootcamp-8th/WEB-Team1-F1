import { axiosInstance } from '@/lib/axios'
import type { QuoteResult } from '@/features/quote/types'

/** POST /api/quotes. 번호판 미등록과 소유자명 불일치는 구분 없이 같은 404로 내려온다. */
export async function estimateQuote(
  plateNumber: string,
  ownerName: string,
  mileage: number,
): Promise<QuoteResult> {
  const { data } = await axiosInstance.post<QuoteResult>('/api/quotes', {
    plateNumber,
    ownerName,
    mileage,
  })
  return data
}
