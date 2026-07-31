import { axiosInstance } from '@/lib/axios'
import type { SellApplicationResult } from '@/features/sell/types'

/**
 * POST /api/sell. 서버가 번호판으로 제원을 재조회해 차량·경매글·경매를 만들고,
 * 시작 시각도 서버가 계산한다(신청 시각 + 1시간) — 클라이언트는 번호판만 보낸다.
 * 세션 쿠키 인증이 필요하다.
 */
export async function applySell(plateNumber: string): Promise<SellApplicationResult> {
  const { data } = await axiosInstance.post<SellApplicationResult>('/api/sell', {
    plateNumber,
  })
  return data
}
