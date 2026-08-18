import type { NotificationType } from '@/types/domain'

/**
 * "내 거래가 방금 바뀌었다"를 뜻하는 알림 종류.
 *
 * 낙찰(AUCTION_WON)이 함께 들어 있는 것은 그 순간 거래가 만들어져 구매 내역에 새 행이 생기기
 * 때문이다. 종류가 늘면 여기에 함께 넣어야 화면이 따라 움직인다.
 */
const DEAL_NOTIFICATION_TYPES: ReadonlySet<NotificationType> = new Set<NotificationType>([
  'AUCTION_WON',
  'DEAL_SELLER_SUBMIT_REQUIRED',
  'DEAL_BUYER_SCHEDULE_REQUIRED',
  'DEAL_CONFIRMED',
  'DEAL_CANCELLED',
])

export function isDealNotification(type: NotificationType): boolean {
  return DEAL_NOTIFICATION_TYPES.has(type)
}

type DealChangedListener = () => void

const listeners = new Set<DealChangedListener>()

/**
 * 거래 목록에게 다시 읽으라고 알린다.
 *
 * 상세는 조회 캐시(react-query) 위에 있어 무효화로 끝나지만, 목록은 feature 훅이 상태를 직접
 * 들고 있어(use-deal-list) 무효화가 닿지 않는다. 목록 하나 때문에 상태 관리 방식을 바꾸는 대신
 * 신호만 따로 흘린다.
 */
export function emitDealChanged(): void {
  for (const listener of [...listeners]) listener()
}

export function subscribeDealChanged(listener: DealChangedListener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}
