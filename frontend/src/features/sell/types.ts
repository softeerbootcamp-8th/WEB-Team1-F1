import type { AuctionStatus } from '@/types/domain'

/** 백엔드 SellApplicationResponse와 동일한 필드 */
export interface SellApplicationResult {
  auctionId: number
  vehicleId: number
  startPrice: number
  startAt: string
  roomOpenAt: string
  endAt: string
  status: AuctionStatus
}

/** POST /api/visit-quotes 요청 계약 */
export interface VisitQuoteRequest {
  plateNumber: string
  ownerName: string
  visitAddress: string
  visitDate: string
  contactPhone: string
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
