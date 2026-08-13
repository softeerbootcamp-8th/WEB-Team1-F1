import { useQuery } from '@tanstack/react-query'

import { fetchMyRequests } from './api'
import type { EvaluationAuctionStatus } from './types'

/** 신청 목록 쿼리 키. 출품 뒤 이 키를 무효화해야 등록 버튼의 판정이 최신이 된다. */
export const MY_REQUESTS_QUERY_KEY = ['evaluations', 'my-requests'] as const

/**
 * 이 신청에 걸린 차량의 최신 경매 상태. 상세 응답에는 경매 상태가 없어 신청 목록에서 읽는다.
 *
 * 목록을 통째로 받아 한 건을 고른다. 마이페이지를 거쳐 들어왔다면 이미 캐시에 있어 요청이
 * 늘지 않고, 딥링크로 바로 들어온 경우에만 한 번 더 나간다. 목록 화면의 경매 배지와 같은
 * 응답을 보므로 배지와 등록 버튼이 서로 다른 말을 하지 않는다.
 *
 * 경매가 한 번도 없었으면 null 이다. 이 null 은 "조회 중"과 구분되지 않으므로, 버튼을 여닫는
 * 쪽에서 isLoading 을 함께 본다.
 */
export function useVehicleAuctionStatus(evaluationId: number, enabled = true) {
  const query = useQuery({
    queryKey: MY_REQUESTS_QUERY_KEY,
    queryFn: fetchMyRequests,
    enabled,
  })

  const auctionStatus: EvaluationAuctionStatus | null =
    query.data?.evaluations.find(
      (evaluation) => evaluation.evaluationId === evaluationId,
    )?.auctionStatus ?? null

  return { auctionStatus, isLoading: query.isLoading }
}
