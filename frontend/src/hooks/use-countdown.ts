import { useEffect, useState } from 'react'

/**
 * 목표 시각(ISO)까지 남은 밀리초를 1초(기본) 간격으로 갱신한다.
 * 대기방 카운트다운·경매 종료 타이머 등에 사용.
 *
 * offsetMs는 서버 시각에서 이 브라우저 시계를 뺀 차이다. 마감 시각은 서버가 정하는데 남은 시간을
 * 브라우저 시계로 세면, 시계가 2분 빠른 사람은 아직 입찰할 수 있는 경매를 끝난 것으로 본다.
 * 서버가 응답마다 실어 주는 시각으로 그 차이를 메운다.
 */
export function useCountdown(targetIso: string, intervalMs = 1000, offsetMs = 0) {
  const target = new Date(targetIso).getTime()
  const [remaining, setRemaining] = useState(() => target - (Date.now() + offsetMs))

  useEffect(() => {
    const now = () => Date.now() + offsetMs

    setRemaining(target - now())
    const id = window.setInterval(() => {
      setRemaining(target - now())
    }, intervalMs)
    return () => window.clearInterval(id)
  }, [target, intervalMs, offsetMs])

  return {
    remaining: Math.max(0, remaining),
    isElapsed: remaining <= 0,
  }
}
