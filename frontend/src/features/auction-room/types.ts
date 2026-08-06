import type { RoomPhase } from '@/features/auctions/types'
// 차량 제원 어휘는 시세 조회 화면이 먼저 정의했고 백엔드 enum과 같은 값이라 그대로 쓴다
import type { FuelType, Manufacturer } from '@/features/quote/types'
import type { UserRole } from '@/types/domain'

export type { RoomPhase }

/**
 * 경매방에 들어가려 한 결과.
 * 서버는 열려 있는 방에만 현황을 주고 그 밖에는 사유를 코드로 알려준다. 개장 전이면 개장 안내로,
 * 끝난 뒤면 결과 요약으로 옮겨가라는 뜻이라 실패를 하나로 뭉치지 않는다.
 */
export type RoomEntry = 'LOADING' | 'OPEN' | 'NOT_OPEN_YET' | 'CLOSED' | 'BROKEN'

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

/** 백엔드 RoomOpeningResponse와 동일한 필드 — 아직 열리지 않은 방의 안내다 */
export interface RoomOpeningView {
  auctionId: number
  vehicle: RoomVehicle
  startPrice: number
  openAt: string
  startAt: string
  serverTime: string
}

/** 백엔드 RoomResultResponse와 동일한 필드 — 더 이상 바뀌지 않는 경매라 접속자 수와 서버 시각이 없다 */
export interface RoomResultView {
  auctionId: number
  outcome: 'SOLD' | 'UNSOLD'
  vehicle: RoomVehicle
  startPrice: number
  /** 유찰이면 null */
  winningPrice: number | null
  /** 유찰이면 null */
  winner: RoomWinner | null
  bidCount: number
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
