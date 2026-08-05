import { axiosInstance } from '@/lib/axios'
import type {
  AuctionListCursor,
  AuctionListGroup,
  AuctionListPage,
  AuctionListScope,
} from '@/features/auctions/types'

export interface AuctionListParams {
  scope: AuctionListScope
  /** null이면 전체 상태를 진행중 → 예정 → 종료 순으로 받는다 */
  filter: AuctionListGroup | null
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
  cursor,
}: AuctionListParams): Promise<AuctionListPage> {
  const { data } = await axiosInstance.get<AuctionListPage>(
    scope === 'MINE' ? '/api/auctions/me' : '/api/auctions',
    { params: { ...cursor, ...(filter ? { filter } : {}) } },
  )
  return data
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
