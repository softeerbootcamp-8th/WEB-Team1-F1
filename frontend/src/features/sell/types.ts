import type { VehicleLookupResponse } from '@/features/vehicle/types'

/** POST /api/auctions 요청 계약 */
export interface AuctionCreateRequest {
  vehicleId: number
  startPrice: number
  /** 백엔드 LocalDateTime 형식(시간대 없는 로컬 시각) */
  startAt: string
}

/** POST /api/auctions 응답 계약. 생성 직후 상태는 항상 SCHEDULED다. */
export interface AuctionCreationResult {
  auctionId: number
  vehicleId: number
  startPrice: number
  startAt: string
  roomOpenAt: string
  endAt: string
  status: 'SCHEDULED'
}

/** POST /api/visit-quotes 요청 계약 */
export interface VisitQuoteRequest {
  plateNumber: string
  ownerName: string
  visitAddress: string
  visitDate: string
  contactPhone: string
}

/** POST /api/visit-quotes/precheck 200 응답 계약 */
export interface VisitQuotePrecheckResponse {
  vehicle: VehicleLookupResponse
  hasInProgressVisitQuote: boolean
}

/** POST /api/visit-quotes 201 응답 계약 */
export interface VisitQuoteResponse {
  evaluationId: number
  vehicleId: number
  plateNumber: string
  visitDate: string
  visitAddress: string
  status: 'REQUESTED'
}
