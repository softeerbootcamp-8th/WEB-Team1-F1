import type { RoomPhase } from '@/features/auctions/types'
import type { UserRole } from '@/types/domain'

export type { RoomPhase }

export interface RoomVehicle {
  manufacturer: string
  model: string
  modelYear: number
  mileage: number
  fuelType: string
}

export interface RecentBid {
  name: string
  role: UserRole
  amount: number
  bidAt: string
  mine: boolean
}

export interface RoomWinner {
  name: string
  mine: boolean
}

/** 백엔드 AuctionRoomResponse와 동일한 필드 */
export interface AuctionRoomView {
  auctionId: number
  phase: RoomPhase
  vehicle: RoomVehicle
  thumbnailUrl: string | null
  startPrice: number
  currentPrice: number
  openAt: string
  startAt: string
  endAt: string
  serverTime: string
  connectedCount: number
  bidderCount: number
  bidCount: number
  winner: RoomWinner | null
  recentBids: RecentBid[]
}

export interface BidPlaceResult {
  bidId: number
  amount: number
  endAt: string
  serverTime: string
}

export interface BidIncrementBand {
  minPrice: number
  increment: number
}
