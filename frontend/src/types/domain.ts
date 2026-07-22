/**
 * 서비스 전역 도메인 타입.
 * 백엔드 스키마가 확정되면 src/api/generated 의 orval 타입으로 교체/정렬한다.
 * 지금은 화면 개발용 계약(contract) 역할.
 */

/** 사용자 역할 — 일반인(판매자) / 딜러(구매자) */
export type UserRole = 'USER' | 'DEALER'

export interface User {
  id: number
  nickname: string
  role: UserRole
  email: string
}

/** 경매 진행 상태 (스케줄러 자동 전이: SCHEDULED → LIVE → ENDED) */
export type AuctionStatus = 'SCHEDULED' | 'LIVE' | 'ENDED'

export interface CarSummary {
  name: string // 차종/모델 (ex. 더 뉴 K5)
  year: number
  mileageKm: number
  fuel: string
  region: string
}

export interface AuctionCard {
  id: number
  title: string
  thumbnailUrl: string
  car: CarSummary
  status: AuctionStatus
  startPrice: number
  currentPrice: number
  bidCount: number
  startAt: string // ISO — 시작 시각
  endAt: string // ISO — 종료 예정 시각
}

/** 호가창 한 줄 (커서 페이지네이션) */
export interface Bid {
  id: number
  bidderNickname: string // 마스킹된 닉네임 (ex. 김X진)
  amount: number
  createdAt: string // ISO
  isMine?: boolean
}

/** 거래(Deal) 상태 파이프라인 */
export type DealStatus =
  | 'PENDING_SELLER'
  | 'CONFIRMED'
  | 'IN_TRANSIT'
  | 'COMPLETED'
  | 'CANCELLED'

/** 이 거래에서 내가 어느 쪽인지 (개인·딜러 모두 양쪽 다 될 수 있다) */
export type DealSide = 'SELLER' | 'BUYER'

export interface Deal {
  id: number
  auctionId: number
  carName: string
  thumbnailUrl: string
  finalPrice: number
  status: DealStatus
  /** 이 거래에서 내 역할(판매자/구매자) — 액션 분기 기준 */
  myRole: DealSide
  counterpartNickname: string
  updatedAt: string
}

/** 알림 */
export type NotificationType =
  | 'EVAL_APPROVED'
  | 'EVAL_REJECTED'
  | 'WON'
  | 'DEAL_UPDATED'

export interface AppNotification {
  id: number
  type: NotificationType
  title: string
  body: string
  createdAt: string
  read: boolean
  link?: string
}
