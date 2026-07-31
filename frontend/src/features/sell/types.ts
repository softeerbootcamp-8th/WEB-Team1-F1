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
