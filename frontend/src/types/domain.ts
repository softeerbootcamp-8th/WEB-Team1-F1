/**
 * 서비스 전역 도메인 타입.
 * 백엔드 스키마가 확정되면 src/api/generated 의 orval 타입으로 교체/정렬한다.
 * 지금은 화면 개발용 계약(contract) 역할.
 */

/** 사용자 역할 — 백엔드 Role enum과 동일. 평가사(EVALUATOR)는 셀프 회원가입 대상이 아니다. */
export type UserRole = 'GENERAL' | 'DEALER' | 'EVALUATOR'

/** 회원가입으로 만들 수 있는 역할만 */
export type SelfSignUpRole = 'GENERAL' | 'DEALER'

/** 백엔드 AuthUserResponse와 동일한 필드 */
export interface User {
  id: number
  username: string
  email: string
  realName: string
  role: UserRole
}

/** 경매 진행 상태 (스케줄러 자동 전이: SCHEDULED → LIVE → ENDED) */
export type AuctionStatus = 'SCHEDULED' | 'LIVE' | 'ENDED'

/**
 * 카드 뱃지가 보여주는 단계. 서버의 3단계 AuctionStatus와 달리 시작 전을 둘로 가른다 —
 * 대기실이 열렸는지(= 지금 들어갈 수 있는지)는 목록에서 알려줄 값이지 상태 필터의 단위가 아니다.
 * 값 이름은 경매방의 RoomPhase·RoomStateMode와 맞춰 같은 단계를 같은 말로 부른다.
 */
export type AuctionBadgeStatus = 'NOT_OPEN' | 'WAITING' | 'LIVE' | 'ENDED'

export interface CarSummary {
  name: string // 차종/모델 (ex. 더 뉴 K5)
  year: number
  mileageKm: number
  fuel: string
  evaluation?: VehicleEvaluation
}

export interface VehicleEvaluation {
  exteriorFrame: string
  accidentHistory: string
  keyOptions: string
  grade: string
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
  participantCount: number
  evaluationKeywords: string[]
  startAt: string // ISO — 시작 시각
  endAt: string // ISO — 종료 예정 시각
}

// 거래 계약은 `features/deals/types.ts` 에 있다. 서버 응답과 1:1이라 feature 안에 둔다.

/**
 * 알림 종류 — 백엔드 NotificationType과 1:1로 맞춘다.
 * 종류가 늘면 벨의 아이콘 대응표도 함께 고쳐야 한다. 대응표가 전수라 빠뜨리면 빌드가 깨진다.
 */
export type NotificationType =
  | 'WELCOME'
  | 'EVAL_APPROVED'
  | 'EVAL_REJECTED'
  | 'AUCTION_WON'
  | 'AUCTION_WON_RESULT'
  | 'AUCTION_ENDED'
  | 'AUCTION_SOLD'
  | 'AUCTION_FAILED'
  | 'DEAL_SELLER_SUBMIT_REQUIRED'
  | 'DEAL_BUYER_SCHEDULE_REQUIRED'
  | 'DEAL_CONFIRMED'
  | 'DEAL_CANCELLED'
  /** 단계별 종류로 대체돼 더는 발행되지 않는다. 이미 쌓인 알림이 이 이름을 들고 있어 지우지 못한다 */
  | 'DEAL_STATUS_CHANGED'

export interface AppNotification {
  id: number
  type: NotificationType
  /** 발행 당시 문구가 그대로 보관된 것. 화면에서 조립하지 않는다 */
  message: string
  read: boolean
  /** 눌렀을 때 갈 곳. 서버가 종류와 참조로 만들어 내려준다 */
  link: string
  createdAt: string
}
