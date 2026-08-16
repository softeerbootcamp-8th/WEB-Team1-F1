import type { RoomPhase } from '@/features/auctions/types'
// 차량 제원 어휘는 시세 조회 화면이 먼저 정의했고 백엔드 enum과 같은 값이라 그대로 쓴다
import type { FuelType, Manufacturer, VehicleKeyword } from '@/features/quote/types'
import type { UserRole } from '@/types/domain'

export type { RoomPhase }

/**
 * 경매방에 들어가려 한 결과.
 * 서버는 열려 있는 방에만 현황을 주고 그 밖에는 사유를 코드로 알려준다. 개장 전이면 개장 안내로,
 * 끝난 뒤면 결과 요약으로 옮겨가라는 뜻이라 실패를 하나로 뭉치지 않는다.
 * UNSTABLE 은 다시 붙어 보다 포기한 것이라, 없는 경매를 뜻하는 BROKEN 과 안내가 달라야 한다.
 * SIGNED_OUT 은 세션이 끊긴 것이라 로그인만 하면 바로 들어갈 수 있다.
 */
export type RoomEntry =
  | 'LOADING'
  | 'OPEN'
  | 'NOT_OPEN_YET'
  | 'CLOSED'
  | 'SIGNED_OUT'
  | 'UNSTABLE'
  | 'BROKEN'

export interface RoomVehicle {
  manufacturer: Manufacturer
  model: string
  modelYear: number
  mileage: number
  fuelType: FuelType
  /** 서버가 표시 순서로 정렬해 보낸다, 진단 키워드가 없으면 빈 배열 */
  keywords: VehicleKeyword[]
  imageUrls: string[]
  /** 출품된 차량은 결과 제출을 거쳤으므로 서버가 항상 채워 보낸다 */
  diagnosticReportUrl: string
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
  viewerCount: number
  bidderCount: number
  bidCount: number
  winner: RoomWinner | null
  /** 조회한 사람이 이 차를 내놓은 사람인지, 판매자는 자기 차량에 입찰할 수 없다 */
  sellerIsMine: boolean
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
/** 방 안에서 바뀌지 않는 값은 방송에 실리지 않는다, 최초 조회가 한 번 준다 */
export interface RoomStreamState {
  auctionId: number
  phase: RoomPhase
  currentPrice: number
  endAt: string
  serverTime: string
  viewerCount: number
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

/** 결과 곡선의 점 하나. 이름은 실리지 않는다 — 누가 불렀는지는 호가창이 답할 일이다 */
export interface PricePoint {
  bidAt: string
  amount: number
  mine: boolean
  /** 이 입찰로 마감이 밀렸는지 */
  extended: boolean
}

/** 조회한 사람의 성적. 입찰한 적이 없으면 이 객체 자체가 null 이다 */
export interface MyStanding {
  highestAmount: number
  /** 입찰한 사람 중 내 순위, 1이면 내가 가장 높았다 */
  rank: number
}

/**
 * 백엔드 RoomResultResponse와 동일한 필드 — 더 이상 바뀌지 않는 경매라 접속자 수도 호가창도 없다.
 * serverTime 은 결과값이 아니라 남은 열람 시간을 세는 기준이라 resultViewingEndsAt 과 짝으로 온다.
 */
export interface RoomResultView {
  auctionId: number
  outcome: 'SOLD' | 'UNSOLD'
  vehicle: RoomVehicle
  startPrice: number
  /** 유찰이면 null */
  winningPrice: number | null
  /** 유찰이면 null */
  winner: RoomWinner | null
  /** 조회한 사람이 이 차를 내놓은 사람인지 */
  sellerIsMine: boolean
  /** 입찰한 적이 없으면 null */
  myStanding: MyStanding | null
  bidCount: number
  bidderCount: number
  /** 마감 임박 입찰로 마감이 밀린 횟수 */
  extensionCount: number
  startAt: string
  /** 연장된 만큼 밀린 최종 마감 시각 */
  endAt: string
  resultViewingEndsAt: string
  serverTime: string
  /** 시간순 전체. 유찰이면 빈 배열이고 시작가는 담기지 않는다 */
  priceCurve: PricePoint[]
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
