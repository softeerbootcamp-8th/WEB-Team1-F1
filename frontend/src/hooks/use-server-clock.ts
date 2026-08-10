import { useEffect, useState } from 'react'

/**
 * 서버 기준 현재 시각을 주기마다 굴린다. 목록이 단계를 시각으로 다시 판정하는 데 쓴다.
 *
 * 화면 하나에 시계도 하나다. 카드마다 따로 두면 같은 순간에 카드끼리 다른 시각을 보게 되고,
 * 그러면 뱃지는 종료인데 자리는 진행중에 남는 어긋남이 생긴다.
 *
 * offsetMs는 서버 시각에서 이 브라우저 시계를 뺀 차이다(serverClockOffset).
 */
export function useServerClock(offsetMs: number, intervalMs = 1000): number {
  const [nowMs, setNowMs] = useState(() => Date.now() + offsetMs)

  useEffect(() => {
    const tick = () => setNowMs(Date.now() + offsetMs)

    // 보정값이 늦게 도착하므로 첫 틱을 기다리지 않고 한 번 맞춘다
    tick()
    const id = window.setInterval(tick, intervalMs)

    return () => window.clearInterval(id)
  }, [offsetMs, intervalMs])

  return nowMs
}
