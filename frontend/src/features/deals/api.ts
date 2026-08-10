import { axiosInstance } from '@/lib/axios'
import type { DealDetail } from './types'

/**
 * 없는 거래와 남의 거래가 모두 404 로 온다. 서버가 둘을 구분해 주지 않으므로 화면도 구분하지 않는다 —
 * 구분하면 그 번호의 거래가 존재한다는 사실이 새어 나간다.
 */
export async function fetchDealDetail(dealId: number): Promise<DealDetail> {
  const { data } = await axiosInstance.get<DealDetail>(`/api/deals/${dealId}`)
  return data
}
