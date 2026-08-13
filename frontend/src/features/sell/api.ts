import { axiosInstance } from '@/lib/axios'
import type {
  AuctionCreateRequest,
  AuctionCreationResult,
  VisitQuotePrecheckResponse,
  VisitQuoteRequest,
  VisitQuoteResponse,
} from '@/features/sell/types'
import type { VehicleLookupRequest } from '@/features/vehicle/types'

/** POST /api/auctions. 진단이 끝난 본인 차량으로 경매를 예약한다. */
export async function createAuction(
  request: AuctionCreateRequest,
): Promise<AuctionCreationResult> {
  const { data } = await axiosInstance.post<AuctionCreationResult>(
    '/api/auctions',
    request,
  )
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

/** 예약 화면 진입 전에 차량과 진행 중인 방문견적 존재 여부를 확인한다. */
export async function precheckVisitQuote(
  request: VehicleLookupRequest,
): Promise<VisitQuotePrecheckResponse> {
  const { data } = await axiosInstance.post<VisitQuotePrecheckResponse>(
    '/api/visit-quotes/precheck',
    request,
  )
  return data
}
