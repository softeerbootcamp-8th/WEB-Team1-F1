import { axiosInstance } from '@/lib/axios'
import type {
  AuctionRoomView,
  BidIncrementBand,
  BidPlaceResult,
  RoomStreamState,
} from '@/features/auction-room/types'

/**
 * GET /api/auctions/{id}/room. 방에 들어갈 때 최초 1회 화면을 그리는 용도다 —
 * 이후 갱신은 반복 조회가 아니라 /room/stream 구독으로 받는다(백엔드 문서).
 * X-User-Id는 인증 도입 전 임시 헤더 — 세션 쿠키가 아니라 헤더로 "누구의 시점인지"를 알려준다.
 */
export async function fetchAuctionRoom(
  auctionId: number,
  userId: number,
): Promise<AuctionRoomView> {
  const { data } = await axiosInstance.get<AuctionRoomView>(
    `/api/auctions/${auctionId}/room`,
    { headers: { 'X-User-Id': userId } },
  )
  return data
}

/**
 * GET /api/auctions/{id}/room/stream 구독(SSE). 방이 열려 있는 단계(WAITING·LIVE·RESULT)에서만
 * 연결이 되고, 그 외(NOT_OPEN·CLOSED)엔 서버가 409로 거절한다. 보는 사람을 가리지 않아
 * 내 입찰(mine) 표시는 안 실려 온다 — 그건 최초 조회 결과로만 안다.
 * 매 전송이 변경분이 아니라 전체 현황이라 하나를 놓쳐도 다음 전송이 덮는다.
 */
export function subscribeRoomStream(
  auctionId: number,
  onState: (state: RoomStreamState) => void,
  onError?: () => void,
): () => void {
  const baseURL = axiosInstance.defaults.baseURL ?? ''
  const source = new EventSource(`${baseURL}/api/auctions/${auctionId}/room/stream`, {
    withCredentials: true,
  })

  source.onmessage = (event) => {
    onState(JSON.parse(event.data) as RoomStreamState)
  }
  source.onerror = () => {
    onError?.()
  }

  return () => source.close()
}

/** POST /api/auctions/{id}/bids. 세션 쿠키 인증이 필요하다. */
export async function placeBid(auctionId: number, amount: number): Promise<BidPlaceResult> {
  const { data } = await axiosInstance.post<BidPlaceResult>(
    `/api/auctions/${auctionId}/bids`,
    { amount },
  )
  return data
}

/** GET /api/bid-increments. DB 시드값이라 클라이언트에 하드코딩하지 않는다. */
export async function fetchBidIncrementBands(): Promise<BidIncrementBand[]> {
  const { data } = await axiosInstance.get<{ bands: BidIncrementBand[] }>(
    '/api/bid-increments',
  )
  return data.bands
}
