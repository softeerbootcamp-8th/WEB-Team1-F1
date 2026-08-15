import { useEffect, useState } from 'react'

import { fetchRoomResult } from '@/features/auction-room/api'
import type { RoomResultView } from '@/features/auction-room/types'
import { getErrorCode, getErrorStatus } from '@/lib/axios'

/**
 * 결과 화면의 진입 상태.
 *
 * PENDING 은 마감은 됐는데 낙찰자가 아직 확정되지 않은 짧은 구간이다. 서버가 그 사이를
 * ROOM_RESULT_NOT_READY 로 알려주므로 없는 경매와 뭉치지 않는다.
 */
export type ResultEntry = 'LOADING' | 'READY' | 'PENDING' | 'SIGNED_OUT' | 'BROKEN'

const HTTP_UNAUTHORIZED = 401

// 확정은 500ms 주기 스케줄러가 한다, 다섯 번이면 마지막 시도까지 15초쯤이라 그 지연을 다 흡수한다
const MAX_ATTEMPTS = 5
const RETRY_BASE_MS = 500

// 같은 경매를 보던 사람들이 같은 박자로 몰려가지 않도록 대기 시간을 조금씩 흩는다
function backoffMs(attempt: number): number {
  return RETRY_BASE_MS * 2 ** attempt * (0.8 + Math.random() * 0.4)
}

/**
 * 끝난 경매의 결과. 방과 달리 구독하지 않는다.
 *
 * 값이 더 바뀌지 않으므로 한 번 받으면 끝이고, 판정 기준이 시각이 아니라 확정된 경매 상태라
 * 결과 확인 구간이 지난 뒤에도 같은 응답이 온다. 그래서 이 화면은 방이 닫혀도 남는다.
 */
export function useAuctionResult(auctionId: number) {
  const [result, setResult] = useState<RoomResultView | null>(null)
  const [entry, setEntry] = useState<ResultEntry>('LOADING')

  useEffect(() => {
    let cancelled = false
    let retryTimer: number | undefined
    let attempts = 0

    const load = () => {
      fetchRoomResult(auctionId)
        .then((view) => {
          if (cancelled) return

          setResult(view)
          setEntry('READY')
        })
        .catch((error: unknown) => {
          if (cancelled) return

          // 인증 실패는 HTTP 가 이미 뜻을 정해 둔 실패다, 어떤 도메인 코드가 붙어 오든 할 일은 같다
          if (getErrorStatus(error) === HTTP_UNAUTHORIZED) {
            setEntry('SIGNED_OUT')
            return
          }

          // 마감과 낙찰 확정 사이의 짧은 틈이다, 확정되면 결과가 나온다
          // 확정이 계속 실패한 채로 남으면 이 물음도 끝나지 않으므로 몇 번만 묻고 그만둔다
          if (getErrorCode(error) === 'ROOM_RESULT_NOT_READY') {
            if (attempts >= MAX_ATTEMPTS) {
              setEntry('PENDING')
              return
            }

            // 첫 물음이 빗나갔다고 바로 안내를 띄우지 않는다. 확정은 0.5초 주기라 대개 다음
            // 물음에서 답이 오고, 그 사이에 안내를 스쳐 보이면 화면이 새로고침한 것처럼 깜빡인다
            if (attempts >= 1) setEntry('PENDING')

            retryTimer = window.setTimeout(load, backoffMs(attempts))
            attempts += 1
            return
          }

          setEntry('BROKEN')
        })
    }

    load()

    return () => {
      cancelled = true
      window.clearTimeout(retryTimer)
    }
  }, [auctionId])

  return { result, entry }
}
