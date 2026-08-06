import type { RoomPhase } from '@/features/auctions/types'
// 차량 제원 어휘는 시세 조회 화면이 먼저 정의했고 백엔드 enum과 같은 값이라 그대로 쓴다
import type { FuelType, Manufacturer } from '@/features/quote/types'
import type { UserRole } from '@/types/domain'

export type { RoomPhase }

export interface RoomVehicle {
  manufacturer: Manufacturer
  model: string
  modelYear: number
  mileage: number
  fuelType: FuelType
  thumbnailUrl: string | null
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

/** 실시간 구독(SSE)이 보는 사람을 가리지 않아 내 입찰 표시가 없는 버전 */
export interface RoomStreamBid {
  name: string
  role: UserRole
  amount: number
  bidAt: string
}

/** 실시간은 방 전체에 같은 값이 나가 낙찰자에도 본인 여부가 없다 */
export interface RoomStreamWinner {
  name: string
}

/** 백엔드 RoomStateResponse와 동일한 필드 — GET /room/stream이 매번 전체 상태를 통째로 밀어준다 */
export interface RoomStreamState {
  auctionId: number
  phase: RoomPhase
  vehicle: RoomVehicle
  startPrice: number
  currentPrice: number
  openAt: string
  startAt: string
  endAt: string
  serverTime: string
  connectedCount: number
  bidderCount: number
  bidCount: number
  winner: RoomStreamWinner | null
  recentBids: RoomStreamBid[]
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
