import { axiosInstance } from '@/lib/axios'
import type { AuctionListCursor, AuctionListPage } from '@/features/auctions/types'

/**
 * GET /api/auctions. 커서 없이 부르면 첫 페이지, 이후에는 직전 응답의
 * nextCursor를 그대로 쿼리 파라미터로 돌려보낸다(부분 커서는 서버가 400).
 */
export async function fetchAuctionList(cursor?: AuctionListCursor): Promise<AuctionListPage> {
  const { data } = await axiosInstance.get<AuctionListPage>('/api/auctions', {
    params: cursor,
  })
  return data
}
