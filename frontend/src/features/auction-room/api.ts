import { axiosInstance } from '@/lib/axios'
import type {
  AuctionRoomView,
  BidIncrementBand,
  BidPlaceResult,
} from '@/features/auction-room/types'

/**
 * GET /api/auctions/{id}/room. 조회 자체가 접속 기록이라 2초 주기로 불러야 한다(백엔드 문서).
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
