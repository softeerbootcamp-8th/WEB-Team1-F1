import { axiosInstance } from '@/lib/axios'
import type { AuctionVehicleFilter } from '@/features/auctions/filter'
import { toFilterParams } from '@/features/auctions/filter'
import type {
  AuctionListCard,
  AuctionListCursor,
  AuctionListGroup,
  AuctionListPage,
  AuctionListScope,
} from '@/features/auctions/types'

export interface AuctionListParams {
  scope: AuctionListScope
  /** null이면 전체 상태를 진행중 → 예정 → 종료 순으로 받는다 */
  filter: AuctionListGroup | null
  /** 차량·가격 조건. 없으면 조건 없이 전부 */
  vehicle?: AuctionVehicleFilter
  cursor?: AuctionListCursor | null
}

/**
 * 경매 목록 조회. 커서 없이 부르면 첫 페이지, 이후에는 직전 응답의 nextCursor를
 * 그대로 쿼리 파라미터로 돌려보낸다(부분 커서는 서버가 400).
 *
 * 커서는 "목록에서의 위치"가 아니라 정렬축(마감·시작 시각) 위의 좌표다. 범위나 필터가
 * 바뀌면 그 좌표는 다른 목록을 가리키므로 반드시 버려야 한다. 필터 불일치는 서버가 400으로
 * 잡아주지만 범위(전체↔나의 경매) 전환은 잡지 못하고 조용히 앞부분이 잘린 페이지를 준다.
 */
export async function fetchAuctionList({
  scope,
  filter,
  vehicle,
  cursor,
}: AuctionListParams): Promise<AuctionListPage> {
  // 연료는 여러 개라 같은 이름이 반복돼야 서버의 List 에 붙는다. 객체로 넘기면 axios 가
  // fuelTypes[] 로 바꿔 이름이 어긋난다.
  const params = vehicle ? toFilterParams(vehicle) : new URLSearchParams()
  if (filter) params.set('filter', filter)
  if (cursor) {
    for (const [key, value] of Object.entries(cursor)) params.set(key, String(value))
  }

  const { data } = await axiosInstance.get<AuctionListPage>(
    scope === 'MINE' ? '/api/auctions/me' : '/api/auctions',
    { params },
  )
  return data
}

/** event: audience 의 본문. 시청자 수는 1초마다 보므로 카드 전체가 아니라 이 둘만 온다 */
interface AudiencePayload {
  auctionId: number
  connectedCount: number
}

export interface AuctionListStreamHandlers {
  onCard: (card: AuctionListCard) => void
  onAudience: (payload: AudiencePayload) => void
  /** 다시 붙었을 때. 첫 연결에서는 불리지 않는다 */
  onReconnect: () => void
}

/**
 * GET /api/auctions/stream 구독(SSE). 목록 조회가 비로그인이라 이 통로도 세션이 필요 없다.
 * 구독 직후에는 아무 이벤트도 오지 않는다 — 서버가 보고 있는 페이지를 모르므로 첫 목록은
 * 조회 API가 주고, 여기서는 그 뒤의 변화만 온다(백엔드 문서).
 * card 는 목록 조회의 카드 한 장과 같은 모양이고 변경분이 아니라 전체라 하나를 놓쳐도 다음이 덮는다.
 * audience 는 사람이 있는 모든 방에서 오므로 내 페이지에 없는 경매의 것도 들어온다.
 */
export function subscribeAuctionListStream({
  onCard,
  onAudience,
  onReconnect,
}: AuctionListStreamHandlers): () => void {
  const baseURL = axiosInstance.defaults.baseURL ?? ''
  const source = new EventSource(`${baseURL}/api/auctions/stream`)

  source.addEventListener('card', (event) => {
    onCard(JSON.parse(event.data) as AuctionListCard)
  })

  source.addEventListener('audience', (event) => {
    onAudience(JSON.parse(event.data) as AudiencePayload)
  })

  // 끊기면 EventSource 가 스스로 다시 붙고 그 사이의 이벤트는 유실이다. 서버가 다시 보내지
  // 않으므로 복구는 재조회뿐이다. 첫 연결은 방금 조회한 목록이 최신이라 알리지 않는다
  let opened = false
  source.onopen = () => {
    if (opened) onReconnect()
    opened = true
  }

  return () => source.close()
}

export interface AuctionUpdatePayload {
  startPrice: number
  /** 서버가 LocalDateTime으로 받는다 — 타임존 없는 현지 시각 문자열이어야 한다 */
  startAt: string
}

export interface AuctionUpdateResult {
  auctionId: number
  vehicleId: number
  startPrice: number
  startAt: string
  roomOpenAt: string
  endAt: string
  status: string
}

/** PATCH /api/auctions/{id}. 경매방이 열리기 전에만 통과한다. */
export async function updateAuction(
  auctionId: number,
  payload: AuctionUpdatePayload,
): Promise<AuctionUpdateResult> {
  const { data } = await axiosInstance.patch<AuctionUpdateResult>(
    `/api/auctions/${auctionId}`,
    payload,
  )
  return data
}

/** DELETE /api/auctions/{id}. 경매가 끝난 뒤에만 통과한다(soft delete). */
export async function deleteAuction(auctionId: number): Promise<void> {
  await axiosInstance.delete(`/api/auctions/${auctionId}`)
}

/**
 * GET /api/auctions/{id}/start-alert. 부르는 회원이 시작 알림을 신청했는지.
 * 없는 경매에도 404가 아니라 false 로 답한다 — 화면을 띄운 본 요청이 존재 여부를 이미 판정했다(백엔드 문서).
 * 발송이 끝나면 신청 기록이 정리되므로 시작 뒤에는 신청했던 회원에게도 false 가 온다.
 */
export async function fetchStartAlertSubscribed(auctionId: number): Promise<boolean> {
  const { data } = await axiosInstance.get<{ subscribed: boolean }>(
    `/api/auctions/${auctionId}/start-alert`,
  )
  return data.subscribed
}

/**
 * PUT /api/auctions/{id}/start-alert. 신청 자원이 하나뿐이라 멱등이고, 재전송해도 상태가 같다 —
 * 처음이면 201, 이미 신청돼 있었으면 204 이며 둘 다 성공이다(백엔드 문서).
 * 취소는 제공하지 않고, 시작 전이 아니면 409 START_ALERT_NOT_OPEN 이다.
 */
export async function subscribeStartAlert(auctionId: number): Promise<void> {
  await axiosInstance.put(`/api/auctions/${auctionId}/start-alert`)
}
