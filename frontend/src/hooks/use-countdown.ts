import { useEffect, useState } from 'react'

/**
 * 목표 시각(ISO)까지 남은 밀리초를 1초(기본) 간격으로 갱신한다.
 * 대기방 카운트다운·경매 종료 타이머 등에 사용.
 */
export function useCountdown(targetIso: string, intervalMs = 1000) {
  const target = new Date(targetIso).getTime()
  const [remaining, setRemaining] = useState(() => target - Date.now())

  useEffect(() => {
    setRemaining(target - Date.now())
    const id = window.setInterval(() => {
      setRemaining(target - Date.now())
    }, intervalMs)
    return () => window.clearInterval(id)
  }, [target, intervalMs])

  return {
    remaining: Math.max(0, remaining),
    isElapsed: remaining <= 0,
  }
}
