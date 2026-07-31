/** 백엔드 AuctionCardResponse의 phase — 경매방 진행 단계 */
export type RoomPhase = 'NOT_OPEN' | 'WAITING' | 'LIVE' | 'RESULT' | 'CLOSED'

/** GET /api/auctions 카드 한 장. 목록 전용 — 경매방 상세(AuctionRoomController)와는 별도 계약이다. */
export interface AuctionListCard {
  auctionId: number
  phase: RoomPhase
  thumbnailUrl: string | null
  model: string
  modelYear: number
  mileage: number
  startPrice: number
  currentPrice: number
  openAt: string
  startAt: string
  endAt: string
  connectedCount: number
}

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
