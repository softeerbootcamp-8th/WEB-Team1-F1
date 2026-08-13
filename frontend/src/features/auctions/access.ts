import type { AuctionListScope } from './types'
import type { UserRole } from '@/types/domain'

/** 평가사는 경매를 볼 수 있지만 판매자가 아니므로 나의 경매 범위는 가질 수 없다. */
export function auctionScopeForRole(
  requestedScope: AuctionListScope,
  role: UserRole | null,
): AuctionListScope {
  return role === 'EVALUATOR' ? 'ALL' : requestedScope
}

export function canViewMyAuctions(role: UserRole | null): boolean {
  return role !== 'EVALUATOR'
}
