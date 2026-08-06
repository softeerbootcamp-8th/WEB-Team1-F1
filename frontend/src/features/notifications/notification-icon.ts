import {
  CheckCheck,
  CircleOff,
  CircleX,
  Gavel,
  HandCoins,
  PackageCheck,
  PartyPopper,
  Trophy,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import type { NotificationType } from '@/types/domain'

/**
 * 종류별 아이콘. 드롭다운과 토스트가 같은 표를 본다 — 같은 알림이 자리마다 다른 그림으로 보이면 안 된다.
 *
 * 전수 대응표라서 백엔드에 종류가 늘고 타입에 반영되면 여기서 빌드가 깨진다. 기본 아이콘으로 덮으면
 * 새 종류가 아무 표시 없이 조용히 나가므로, 규칙이 아니라 타입으로 막는다.
 */
export const NOTIFICATION_ICON: Record<NotificationType, LucideIcon> = {
  WELCOME: PartyPopper,
  EVAL_APPROVED: CheckCheck,
  EVAL_REJECTED: CircleX,
  AUCTION_WON: Trophy,
  AUCTION_WON_RESULT: Trophy,
  AUCTION_ENDED: Gavel,
  AUCTION_SOLD: HandCoins,
  AUCTION_FAILED: CircleOff,
  DEAL_STATUS_CHANGED: PackageCheck,
}
