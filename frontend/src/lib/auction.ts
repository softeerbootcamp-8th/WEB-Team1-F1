import type { AuctionBadgeStatus, AuctionStatus } from '@/types/domain'
import type {
  AuctionListCard,
  AuctionListGroup,
  AuctionListScope,
} from '@/features/auctions/types'
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
 * 지금 시각으로 판정한 뱃지 단계. 서버가 준 phase는 조회 시각의 값이라 시간이 지나면 낡는다.
 *
 * 시작 전 둘을 나누는 이유는, 방 개설이 시작 30분 전이라 예정 카드 대부분이 아직 입장 전이고
 * 그중 입장 가능한 소수를 골라내는 것이 이 뱃지의 목적이기 때문이다.
 *
 * 마감 뒤는 나누지 않는다. 결과 확인 구간을 따로 보이면 아직 참여할 수 있는 것처럼 읽히고,
 * 나누지 않은 덕분에 마감 + 5분이라는 서버 상수를 화면이 복제하지 않아도 된다.
 */
export function badgeStatusAt(card: AuctionListCard, nowMs: number): AuctionBadgeStatus {
  if (nowMs < new Date(card.openAt).getTime()) return 'NOT_OPEN'
  if (nowMs < new Date(card.startAt).getTime()) return 'WAITING'
  if (nowMs < new Date(card.endAt).getTime()) return 'LIVE'
  return 'ENDED'
}

/** 화면의 상태 탭을 목록 API의 filter 값으로. 서버는 "예정"을 PENDING이라 부른다. */
export function statusToListGroup(status: AuctionStatus): AuctionListGroup {
  return status === 'SCHEDULED' ? 'PENDING' : status
}

/**
 * 지금 시각으로 다시 판정한 목록 그룹. 서버가 준 phase는 조회 시각의 값이라 시간이 지나면 낡는다.
 * 경계는 서버 쿼리와 같다 — 시작 전은 예정, 시작했고 마감 전이면 진행중, 마감했으면 종료다.
 * 개장(NOT_OPEN→WAITING)과 결과 열람 종료(RESULT→CLOSED)는 그룹을 바꾸지 않으므로 보지 않는다.
 */
export function listGroupAt(card: AuctionListCard, nowMs: number): AuctionListGroup {
  if (nowMs < new Date(card.startAt).getTime()) return 'PENDING'
  if (nowMs < new Date(card.endAt).getTime()) return 'LIVE'
  return 'ENDED'
}

/**
 * 지금 시각으로 그룹을 다시 판정해 카드를 재배치한다. 필터가 있으면 그 그룹만 남긴다.
 *
 * 정렬하지 않는다. 서버가 [진행중 마감임박순][예정 시작임박순][종료 최근마감순] 순으로 주므로
 * 순서를 유지한 채 셋으로 나눠 담고 이어 붙이면 자리가 맞는다. 마감된 카드는 진행중 무리의 맨 앞에
 * 있었으니 종료 무리보다 먼저 담겨 그 맨 앞에 서고, 시작한 카드는 진행중을 다 지난 뒤에 만나므로
 * 그 맨 뒤에 선다. 덕분에 화면이 복제하는 것은 그룹 경계 하나뿐이고 정렬 규칙 셋은 서버에만 남는다.
 */
export function arrangeCards(
  cards: AuctionListCard[],
  nowMs: number,
  filter: AuctionListGroup | null,
): AuctionListCard[] {
  const grouped: Record<AuctionListGroup, AuctionListCard[]> = {
    LIVE: [],
    PENDING: [],
    ENDED: [],
  }

  for (const card of cards) {
    grouped[listGroupAt(card, nowMs)].push(card)
  }

  if (filter) return grouped[filter]

  return [...grouped.LIVE, ...grouped.PENDING, ...grouped.ENDED]
}

/**
 * 스트림으로 온 카드 한 장을 목록에 반영한 새 배열. 바뀔 것이 없으면 받은 배열을 그대로 돌려준다.
 *
 * 자리는 옮기지 않는다. 그것은 arrangeCards 가 지금 시각으로 판정할 몫이라, 여기서 순서까지
 * 건드리면 그룹 경계 판정이 두 곳에 생긴다.
 *
 * 목록에 없던 경매는 진행중일 때만 뒤에 붙인다. 종료 무리는 커서로 끊어 읽는 창이라 못 보던
 * 카드를 끼우면 우리가 읽은 페이지 범위와 어긋난다. 진행중은 첫 페이지가 늘 앞에서부터 담고
 * arrangeCards 가 그 무리의 맨 뒤에 세우는데, 방금 시작한 경매는 마감이 제일 늦어 그 자리가 맞다.
 */
export function applyCardEvent(
  cards: AuctionListCard[],
  incoming: AuctionListCard,
  scope: AuctionListScope,
  nowMs: number,
): AuctionListCard[] {
  const index = cards.findIndex((it) => it.auctionId === incoming.auctionId)

  if (index >= 0) {
    const next = [...cards]
    next[index] = incoming
    return next
  }

  // 스트림은 전체 경매를 흘리므로 나의 경매 목록에 남의 경매가 들어올 수 있다
  if (scope === 'MINE') return cards

  if (listGroupAt(incoming, nowMs) !== 'LIVE') return cards

  return [...cards, incoming]
}

/**
 * 수정 가능 여부. 서버는 경매방이 열리기 전(now < roomOpenAt)만 허용하고,
 * 그 구간이 곧 입장 전 단계다. 방이 열린 뒤 요청은 서버가 거부한다.
 */
// 뱃지 단계를 받는다. 카드가 뱃지를 시각으로 그리므로 버튼도 같은 판정을 써야
// 한 카드 안에서 뱃지와 버튼이 어긋나지 않는다
export function canEditAuction(status: AuctionBadgeStatus): boolean {
  return status === 'NOT_OPEN'
}

/**
 * 삭제 가능 여부. 서버는 낙찰·유찰로 끝난 경매만 지울 수 있게 한다.
 * 단계는 시각으로 재는 값이라 마감 직후엔 서버의 상태 전환이 아직 안 끝났을 수 있고,
 * 그때는 서버가 거부하므로 응답 메시지를 그대로 보여준다.
 */
export function canDeleteAuction(status: AuctionBadgeStatus): boolean {
  return status === 'ENDED'
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
