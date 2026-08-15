import type { Manufacturer, VehicleKeyword } from '@/features/quote/types'

/** 백엔드 AuctionCardResponse의 phase — 경매방 진행 단계 */
export type RoomPhase = 'NOT_OPEN' | 'WAITING' | 'LIVE' | 'RESULT' | 'CLOSED'

/** GET /api/auctions 카드 한 장. 목록 전용 — 경매방 상세(AuctionRoomController)와는 별도 계약이다. */
export interface AuctionListCard {
  auctionId: number
  phase: RoomPhase
  thumbnailUrl: string | null
  manufacturer: Manufacturer
  model: string
  modelYear: number
  mileage: number
  // 평가사가 매긴 키워드. 진단을 거치지 않은 차량은 빈 배열이다
  keywords: VehicleKeyword[]
  startPrice: number
  currentPrice: number
  openAt: string
  startAt: string
  endAt: string
  viewerCount: number
}

/**
 * 목록을 어느 범위로 볼지. 전체는 GET /api/auctions, 나의 경매는 GET /api/auctions/me.
 * 범위가 다르면 아예 다른 목록이라 커서를 공유하지 않는다.
 */
export type AuctionListScope = 'ALL' | 'MINE'

/** 백엔드 AuctionListGroup — 목록의 상태 필터이자 커서의 그룹 순번(진행중 1, 예정 2, 종료 3) */
export type AuctionListGroup = 'LIVE' | 'PENDING' | 'ENDED'

/** 다음 페이지 요청에 그대로 돌려보낼 커서 */
export interface AuctionListCursor {
  snapshotAt: string
  sortPriority: number
  sortAt: string
  auctionId: number
}

export interface AuctionListPage {
  content: AuctionListCard[]
  serverTime: string
  hasNext: boolean
  nextCursor: AuctionListCursor | null
}
