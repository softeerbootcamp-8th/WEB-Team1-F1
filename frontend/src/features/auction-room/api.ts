import { axiosInstance } from '@/lib/axios'
import type {
  AuctionRoomView,
  BidIncrementBand,
  BidPlaceResult,
  RoomOpeningView,
  RoomResultView,
  RoomStreamState,
  RoomViewerCount,
} from '@/features/auction-room/types'

/**
 * GET /api/auctions/{id}/room. 방에 들어갈 때 최초 1회 화면을 그리는 용도다.
 * 이후 갱신은 반복 조회가 아니라 /room/stream 구독으로 받는다(백엔드 문서).
 * 내 입찰과 낙찰자 본인 여부는 세션 주인 기준으로 서버가 판정하므로 신원을 따로 실어 보내지 않는다.
 */
export async function fetchAuctionRoom(auctionId: number): Promise<AuctionRoomView> {
  const { data } = await axiosInstance.get<AuctionRoomView>(`/api/auctions/${auctionId}/room`)
  return data
}

/**
 * GET /api/auctions/{id}/room/opening. 아직 열리지 않은 방의 안내다.
 * 남은 시간은 서버가 세지 않는다 — 입장 가능 시각과 서버 시각의 차이로 화면이 센다(백엔드 문서).
 * 방이 열리면 이 API는 409가 되고 방 조회로 옮겨가야 한다.
 */
export async function fetchRoomOpening(auctionId: number): Promise<RoomOpeningView> {
  const { data } = await axiosInstance.get<RoomOpeningView>(
    `/api/auctions/${auctionId}/room/opening`,
  )
  return data
}

/**
 * GET /api/auctions/{id}/room/result. 끝난 경매의 결과 요약이다.
 * 판정 기준은 마감 시각이 아니라 확정된 경매 상태라, 마감 직후 확정 전에는 409다(백엔드 문서).
 * 낙찰자 본인 여부가 세션 주인 기준으로 판정되므로 세션 쿠키가 필요하다.
 */
export async function fetchRoomResult(auctionId: number): Promise<RoomResultView> {
  const { data } = await axiosInstance.get<RoomResultView>(
    `/api/auctions/${auctionId}/room/result`,
  )
  return data
}

/**
 * GET /api/auctions/{id}/room/stream 구독(SSE). 입찰이 진행되는 동안(WAITING·LIVE)에만 연결되고
 * 그 외에는 서버가 409로 거절한다. 마감되면 마지막 현황을 한 번 보내고 서버가 끊으므로 다시
 * 구독하지 말고 결과 요약으로 가야 한다(RESULT 는 ROOM_STREAM_ENDED, 그 뒤는 ROOM_ALREADY_CLOSED).
 * 보는 사람을 가리지 않아 내 입찰(mine) 표시도 낙찰자 본인 여부도 안 실려 온다.
 * 사람 수는 들고 나는 것만으로 바뀌어 event: viewers 로 따로 오고, 현황에는 실리지 않는다.
 * 둘 다 변경분이 아니라 그 종류의 전체 값이라 하나를 놓쳐도 같은 종류의 다음 전송이 덮는다.
 */
export function subscribeRoomStream(
  auctionId: number,
  onState: (state: RoomStreamState) => void,
  onViewers: (viewers: RoomViewerCount) => void,
  onClosed?: () => void,
): () => void {
  const baseURL = axiosInstance.defaults.baseURL ?? ''
  const source = new EventSource(`${baseURL}/api/auctions/${auctionId}/room/stream`, {
    withCredentials: true,
  })

  source.onmessage = (event) => {
    onState(JSON.parse(event.data) as RoomStreamState)
  }

  source.addEventListener('viewers', (event) => {
    onViewers(JSON.parse(event.data) as RoomViewerCount)
  })

  // 끊기면 EventSource 가 스스로 다시 붙는다. 다만 재연결 응답이 2xx 가 아니면 표준대로 재시도를
  // 포기하고 CLOSED 로 남으므로, 그때만 알린다. 방이 닫혀 서버가 끊은 경우가 여기로 온다
  source.onerror = () => {
    if (source.readyState === EventSource.CLOSED) onClosed?.()
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
