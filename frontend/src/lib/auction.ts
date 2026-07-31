import type { AuctionStatus, DealStatus } from '@/types/domain'
import type { RoomPhase } from '@/features/auctions/types'

/**
 * 호가 단위(최소 인상폭) 자동 계산.
 * 현재가 구간에 따라 단위를 키운다. (백엔드 규칙과 반드시 일치시킬 것)
 */
export function bidIncrement(currentPrice: number): number {
  if (currentPrice < 3_000_000) return 50_000
  if (currentPrice < 10_000_000) return 100_000
  if (currentPrice < 30_000_000) return 250_000
  return 500_000
}

/** 다음 최소 입찰가 = 현재가 + 호가 단위 */
export function nextMinBid(currentPrice: number): number {
  return currentPrice + bidIncrement(currentPrice)
}

/** 소프트 클로즈 임계 — 남은 시간이 이 값 이하이면 마감 임박 */
export const SOFT_CLOSE_THRESHOLD_MS = 30_000

/** 경매 상태 뱃지 메타 (라벨 + Badge variant) */
export const AUCTION_STATUS_META: Record<
  AuctionStatus,
  { label: string; variant: 'live' | 'scheduled' | 'ended' }
> = {
  LIVE: { label: '진행중', variant: 'live' },
  SCHEDULED: { label: '예정', variant: 'scheduled' },
  ENDED: { label: '종료', variant: 'ended' },
}

/**
 * 백엔드 5단계 RoomPhase를 화면의 3단계 AuctionStatus로 좁힌다.
 * NOT_OPEN·WAITING은 아직 입찰 전이라 "예정"으로, RESULT·CLOSED는 "종료"로 묶는다.
 */
export function roomPhaseToStatus(phase: RoomPhase): AuctionStatus {
  if (phase === 'LIVE') return 'LIVE'
  if (phase === 'NOT_OPEN' || phase === 'WAITING') return 'SCHEDULED'
  return 'ENDED'
}

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
