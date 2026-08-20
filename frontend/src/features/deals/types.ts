/**
 * 거래 API 계약. 백엔드 deal 도메인의 응답 레코드와 1:1로 맞춘다.
 *
 * 단계 이름은 서버가 단일 소스라 화면에서 임의로 늘리거나 줄이지 않는다. 경매 목록이
 * `features/auctions/types.ts` 를 따로 둔 것과 같은 자리다.
 */

/**
 * 거래 단계. 단계마다 움직일 수 있는 사람이 정확히 한 명이고 반대쪽은 대기다.
 * 지금 누구 차례인지는 화면이 계산하지 않는다 — 서버가 `actionRequired` 로 내려준다.
 */
export type DealStatus =
  | 'BUYER_CONFIRM_PENDING'
  | 'SELLER_SUBMIT_PENDING'
  | 'BUYER_SCHEDULE_PENDING'
  | 'CONFIRMED'
  | 'CANCELLED'

/** 이 거래에서 조회한 사람이 선 쪽 */
export type DealSide = 'SELLER' | 'BUYER'

/** 거래가 깨진 책임이 어느 쪽에 있는지 */
export type FaultParty = 'SELLER' | 'BUYER'

export type CancellationReason = 'BUYER_CANCELLED' | 'SELLER_CANCELLED'

/** GET /api/deals 의 카드 한 장. 상세와 나눈다 — 목록은 여러 건을 한 번에 읽는다 */
export interface DealCard {
  dealId: number
  auctionId: number
  status: DealStatus
  mySide: DealSide
  finalPrice: number
  model: string
  thumbnailUrl: string | null
  counterpartName: string
  statusChangedAt: string
  /** 지금 내가 움직일 차례인지. 목록에서 가장 먼저 보고 싶은 값이다 */
  actionRequired: boolean
}

/** GET /api/deals 한 페이지. 첫 요청은 커서 없이, 이후는 nextCursor 를 그대로 돌려보낸다 */
export interface DealSlice {
  content: DealCard[]
  serverTime: string
  hasNext: boolean
  nextCursor: number | null
}

/** POST /api/deals/{dealId}/documents/presigned 의 응답 */
export interface DealDocumentUpload {
  key: string
  /** 이 주소로 파일을 PUT 한다. 발급 때 적은 형식·크기와 정확히 같아야 한다 */
  uploadUrl: string
  /** 업로드 후 조회할 주소. 탁송 제출의 documentUrl 로 보내야 하는 값이 이쪽이다 */
  fileUrl: string
  expiresAt: string
}

/** POST /api/deals/{dealId}/transport */
export interface TransportSubmitRequest {
  /** 업로드 API 로 먼저 올리고 받은 조회 주소 */
  documentUrl: string
  transportAt: string
  transportLocation: string
}

/** POST /api/deals/{dealId}/delivery. 동의 여부를 따로 보내지 않는다 — 보내는 것이 곧 동의다 */
export interface DeliveryConfirmRequest {
  deliveryAt: string
  deliveryLocation: string
}

/** GET /api/deals/{dealId} */
export interface DealDetail {
  dealId: number
  auctionId: number
  status: DealStatus
  mySide: DealSide
  finalPrice: number
  model: string
  modelYear: number
  mileage: number
  thumbnailUrl: string | null
  /** 가운데를 가린 상대 이름 */
  counterpartName: string
  /** 거래가 열린 시각 = 낙찰이 확정된 시각 */
  openedAt: string
  statusChangedAt: string
  cancellationReason: CancellationReason | null
  faultParty: FaultParty | null
  /** 판매자가 낸 명의이전 서류, 아직 없으면 null */
  documentUrl: string | null
  /** 판매자가 차를 넘기는 시각과 자리 */
  transportAt: string | null
  transportLocation: string | null
  /** 구매자가 차를 받는 시각과 자리 */
  deliveryAt: string | null
  deliveryLocation: string | null
  /** 지금 내가 움직일 차례인지. 화면이 단계 표를 따로 들고 판정하지 않는다 */
  actionRequired: boolean
  serverTime: string
}

/**
 * 진행 순서. 취소는 어느 단계에서든 빠져나가는 것이라 이 줄에 없다.
 */
export const DEAL_FLOW: DealStatus[] = [
  'BUYER_CONFIRM_PENDING',
  'SELLER_SUBMIT_PENDING',
  'BUYER_SCHEDULE_PENDING',
  'CONFIRMED',
]

/**
 * 단계별 문구. 서버가 단계 이름만 내려주므로 표시 문구는 화면이 갖는다.
 * 전수 대응표라 백엔드에 단계가 늘고 타입에 반영되면 여기서 빌드가 깨진다.
 */
export const DEAL_STATUS_META: Record<DealStatus, { label: string; step: string }> = {
  BUYER_CONFIRM_PENDING: { label: '구매 확정 대기', step: '구매 확정' },
  SELLER_SUBMIT_PENDING: { label: '서류·탁송 등록 대기', step: '서류·탁송' },
  BUYER_SCHEDULE_PENDING: { label: '차량 인수 일정 대기', step: '인수 일정' },
  CONFIRMED: { label: '거래 확정', step: '확정' },
  CANCELLED: { label: '거래 취소', step: '취소' },
}

/**
 * 지금 이 단계에서 무엇을 기다리는지. 내 차례인지 아닌지에 따라 문장이 달라야
 * "나는 기다리면 되는가"가 한눈에 읽힌다.
 */
export function dealGuide(status: DealStatus, actionRequired: boolean): string {
  if (status === 'CANCELLED') {
    return '거래가 취소되었습니다.'
  }
  if (status === 'CONFIRMED') {
    return '약속이 정해졌습니다. 정해진 날짜와 장소에서 차량과 대금을 주고받습니다.'
  }

  const mine: Record<Exclude<DealStatus, 'CONFIRMED' | 'CANCELLED'>, string> = {
    BUYER_CONFIRM_PENDING: '구매 확정 필요',
    SELLER_SUBMIT_PENDING: '서류·탁송 등록 필요',
    BUYER_SCHEDULE_PENDING: '인수 일정 입력 필요',
  }
  const theirs: Record<Exclude<DealStatus, 'CONFIRMED' | 'CANCELLED'>, string> = {
    BUYER_CONFIRM_PENDING: '구매자의 구매 확정을 기다리고 있습니다.',
    SELLER_SUBMIT_PENDING: '판매자의 명의이전 서류와 탁송 일정을 기다리고 있습니다.',
    BUYER_SCHEDULE_PENDING: '구매자의 차량 인수 일정을 기다리고 있습니다.',
  }

  return actionRequired ? mine[status] : theirs[status]
}
