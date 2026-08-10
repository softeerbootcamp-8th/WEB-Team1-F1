import type { AuctionBadgeStatus, AuctionStatus, DealStatus } from '@/types/domain'
import type { AuctionListGroup, RoomPhase } from '@/features/auctions/types'
import type { BidIncrementBand } from '@/features/auction-room/types'

/**
 * 가격대별 최저 상승가 계산. 구간표는 DB 시드값(GET /api/bid-increments)이라
 * 클라이언트에 값을 박지 않고 반드시 받아온 bands로 계산한다.
 * 담당 구간이 없으면 null — 서버도 같은 경우 중단한다(BidIncrementTable.bandOf).
 * 0을 돌려주면 올리지 않아도 되는 입찰을 안내하게 되고, 그 입찰은 서버가 거부한다.
 */
export function incrementForPrice(
  price: number,
  bands: BidIncrementBand[],
): number | null {
  const sorted = [...bands].sort((a, b) => a.minPrice - b.minPrice)
  const band = [...sorted].reverse().find((b) => b.minPrice <= price)
  return band?.increment ?? null
}

/** 소프트 클로즈 임계 — 남은 시간이 이 값 이하이면 마감 임박 */
export const SOFT_CLOSE_THRESHOLD_MS = 30_000

/**
 * 경매 상태 뱃지 메타 (라벨 + Badge variant).
 * 시작 전 두 단계는 "입장"이라는 한 축의 전/후로 부른다. 경매방(RoomStateBanner)과 같은 말이라
 * 목록에서 뱃지를 보고 들어간 사람이 같은 상태를 두 이름으로 만나지 않는다.
 */
export const AUCTION_BADGE_META: Record<
  AuctionBadgeStatus,
  { label: string; variant: 'live' | 'scheduled' | 'waiting' | 'ended' }
> = {
  NOT_OPEN: { label: '입장 전', variant: 'scheduled' },
  WAITING: { label: '입장 가능', variant: 'waiting' },
  LIVE: { label: '진행중', variant: 'live' },
  ENDED: { label: '종료', variant: 'ended' },
}

/**
 * 백엔드 5단계 RoomPhase를 뱃지의 4단계로 좁힌다.
 * RESULT·CLOSED만 "종료"로 묶고, 시작 전 두 단계는 그대로 둔다 —
 * 방 개설은 시작 30분 전이라 예정 카드 대부분이 NOT_OPEN이고, 그 안에서 입장 가능한
 * 소수를 골라내는 것이 이 뱃지의 목적이다.
 */
export function roomPhaseToBadgeStatus(phase: RoomPhase): AuctionBadgeStatus {
  if (phase === 'RESULT' || phase === 'CLOSED') return 'ENDED'
  return phase
}

/** 화면의 상태 탭을 목록 API의 filter 값으로. 서버는 "예정"을 PENDING이라 부른다. */
export function statusToListGroup(status: AuctionStatus): AuctionListGroup {
  return status === 'SCHEDULED' ? 'PENDING' : status
}

/**
 * 수정 가능 여부. 서버는 경매방이 열리기 전(now < roomOpenAt)만 허용하고,
 * 그 구간이 곧 NOT_OPEN 단계다. 방이 열린 뒤 요청은 서버가 거부한다.
 */
export function canEditAuction(phase: RoomPhase): boolean {
  return phase === 'NOT_OPEN'
}

/**
 * 삭제 가능 여부. 서버는 낙찰·유찰로 끝난 경매만 지울 수 있게 한다.
 * 단계는 시각으로 재는 값이라 마감 직후엔 서버의 상태 전환이 아직 안 끝났을 수 있고,
 * 그때는 서버가 거부하므로 응답 메시지를 그대로 보여준다.
 */
export function canDeleteAuction(phase: RoomPhase): boolean {
  return phase === 'RESULT' || phase === 'CLOSED'
}

/**
 * 서버 시각과 그 응답을 받은 순간의 브라우저 시계 차이. 남은 시간을 서버 기준으로 세는 데 쓴다.
 * 서버는 남은 시간을 내려주지 않고 절대 시각과 serverTime 만 주며, 세는 것은 화면 몫이다.
 *
 * 받은 순간을 인자로 받는다. 나중에 Date.now()로 계산하면 조회 이후 흐른 시간만큼 보정값이
 * 어긋나므로, 언제 잡아야 하는지를 시그니처가 강제하게 둔다.
 */
export function serverClockOffset(serverTimeIso: string, receivedAtMs: number): number {
  return new Date(serverTimeIso).getTime() - receivedAtMs
}

/** 경매 시작 시각 최소 리드타임(서버 MIN_LEAD_TIME_HOURS와 같은 1시간) */
export const MIN_START_LEAD_TIME_MS = 60 * 60 * 1000

/** 거래 파이프라인 단계 순서 (진행률 계산용) */
export const DEAL_FLOW: DealStatus[] = [
  'PENDING_SELLER',
  'CONFIRMED',
  'IN_TRANSIT',
  'COMPLETED',
]

export const DEAL_STATUS_META: Record<
  DealStatus,
  { label: string; description: string }
> = {
  PENDING_SELLER: {
    label: '판매자 확정 대기',
    description: '판매자가 거래를 확정하면 다음 단계로 진행됩니다.',
  },
  CONFIRMED: {
    label: '거래 확정',
    description: '거래가 확정되었습니다. 탁송/배송을 준비하세요.',
  },
  IN_TRANSIT: {
    label: '배송중',
    description: '차량이 배송(탁송) 중입니다.',
  },
  COMPLETED: {
    label: '거래 완료',
    description: '거래가 정상적으로 완료되었습니다.',
  },
  CANCELLED: {
    label: '거래 취소',
    description: '거래가 취소되었습니다.',
  },
}

/** 거래 진행률(0~100). CANCELLED 는 0 취급. */
export function dealProgress(status: DealStatus): number {
  if (status === 'CANCELLED') return 0
  const idx = DEAL_FLOW.indexOf(status)
  return ((idx + 1) / DEAL_FLOW.length) * 100
}
