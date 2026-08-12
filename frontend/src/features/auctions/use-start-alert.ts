import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'

import { getErrorCode, getErrorMessage, getErrorStatus } from '@/lib/axios'
import { fetchStartAlertSubscribed, subscribeStartAlert } from '@/features/auctions/api'

const HTTP_UNAUTHORIZED = 401

/** 신청 여부는 화면이 아니라 서버에 저장된 결과가 진실이라, 두 화면이 같은 캐시 키를 본다 */
export function startAlertQueryKey(auctionId: number) {
  return ['auction-start-alert', auctionId] as const
}

export type StartAlertPhase = 'LOADING' | 'IDLE' | 'PENDING' | 'DONE' | 'UNAVAILABLE'

/**
 * 경매 시작 알림 신청 상태.
 *
 * 미리보기와 대기방이 각자 상태를 추측하지 않고 같은 조회 결과를 본다. react-query 캐시를 키
 * 하나로 공유하므로 한쪽에서 신청하면 다른 화면이 다시 물어보지 않아도 완료로 바뀌고,
 * 새로고침 뒤에도 저장된 결과를 다시 읽어 같은 상태가 나온다.
 *
 * 취소가 없어 상태 전이가 한 방향뿐이다 — 신청 전 → 신청 중 → 완료. 완료에서 되돌아오지 않는다.
 */
export function useStartAlert(auctionId: number, enabled: boolean) {
  const queryClient = useQueryClient()
  // 서버가 시작 전이 아니라고 답한 경우. 신청 자체가 불가능해 버튼을 내린다
  const [unavailable, setUnavailable] = useState(false)

  const query = useQuery({
    queryKey: startAlertQueryKey(auctionId),
    queryFn: () => fetchStartAlertSubscribed(auctionId),
    enabled: enabled && Number.isFinite(auctionId),
    // 완료가 되돌아오지 않는 값이라 창을 오갈 때마다 다시 물을 이유가 없다
    staleTime: Infinity,
  })

  const mutation = useMutation({
    mutationFn: () => subscribeStartAlert(auctionId),
    // 낙관적 갱신을 하지 않는다. 신청은 되돌릴 수 없는 안내를 걸고 누르는 버튼이라,
    // 실패했는데 완료로 보였다가 되돌아가는 편이 잠깐 기다리는 것보다 나쁘다
    onSuccess: () => {
      queryClient.setQueryData(startAlertQueryKey(auctionId), true)
    },
    onError: (error: unknown) => {
      if (getErrorCode(error) === 'START_ALERT_NOT_OPEN') setUnavailable(true)
      // 401 은 로그인 화면으로 옮겨 갈 일이라 여기서 토스트로 끝내지 않는다
      if (getErrorStatus(error) === HTTP_UNAUTHORIZED) return
      toast.error(getErrorMessage(error, '알림 신청에 실패했습니다'))
    },
  })

  const phase: StartAlertPhase = unavailable
    ? 'UNAVAILABLE'
    : query.data === true
      ? 'DONE'
      : mutation.isPending
        ? 'PENDING'
        : query.isLoading
          ? 'LOADING'
          : 'IDLE'

  return {
    phase,
    /** 세션이 끊긴 채 눌렀는지. 로그인으로 보내야 한다 */
    signedOut: getErrorStatus(mutation.error) === HTTP_UNAUTHORIZED,
    // 중복 요청 차단은 화면의 disabled 가 아니라 여기서 한 번 더 막는다.
    // 버튼이 두 화면에 있고, 서버도 멱등이지만 굳이 두 번 보낼 이유가 없다
    subscribe: () => {
      if (mutation.isPending || query.data === true || unavailable) return
      mutation.mutate()
    },
  }
}
