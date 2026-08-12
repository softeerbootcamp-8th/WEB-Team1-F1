import type { UserRole } from '@/types/domain'

/**
 * 입찰 자체를 권하지 않는 사유. 기다린다고 열리지 않고 방에 있는 내내 안 되는 둘뿐이다.
 *
 * 마감이나 이미 최고가는 여기 넣지 않는다. 매 순간 바뀌는 조건이라 화면이 서버보다 늦고,
 * 미리 막으면 서버가 받아 줬을 입찰을 화면이 거절하게 된다.
 */
export type BidBlock = 'EVALUATOR' | 'SELLER'

/** 서버 판정 순서를 그대로 따른다, 자기 차를 내놓은 평가사에게 화면과 서버가 다른 사유를 말하지 않게 */
export function bidBlockOf(role: UserRole, sellerIsMine: boolean): BidBlock | null {
  if (role === 'EVALUATOR') return 'EVALUATOR'
  if (sellerIsMine) return 'SELLER'

  return null
}
