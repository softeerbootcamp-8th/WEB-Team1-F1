import { axiosInstance } from '@/lib/axios'
import type {
  SellApplicationResult,
  VisitQuoteRequest,
  VisitQuoteResponse,
} from '@/features/sell/types'

/**
 * POST /api/sell. 서버가 번호판으로 제원을 재조회해 차량·경매글·경매를 만들고,
 * 시작 시각도 서버가 계산한다(신청 시각 + 1시간) — 클라이언트는 번호판과
 * 현재 주행거리를 보낸다.
 * 세션 쿠키 인증이 필요하다.
 */
export async function applySell(
  plateNumber: string,
  mileage: number,
): Promise<SellApplicationResult> {
  const { data } = await axiosInstance.post<SellApplicationResult>('/api/sell', {
    plateNumber,
    mileage,
  })
  return data
}

/** POST /api/visit-quotes. 세션 쿠키 인증이 필요한 방문견적 신청 API다. */
export async function requestVisitQuote(
  request: VisitQuoteRequest,
): Promise<VisitQuoteResponse> {
  const { data } = await axiosInstance.post<VisitQuoteResponse>(
    '/api/visit-quotes',
    request,
  )
  return data
}
