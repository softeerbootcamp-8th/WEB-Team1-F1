import { useCountdown } from '@/hooks/use-countdown'
import { formatDuration } from '@/lib/format'
import { SOFT_CLOSE_THRESHOLD_MS } from '@/lib/auction'
import { cn } from '@/lib/utils'

interface CountdownProps {
  /** 목표(종료/시작) 시각 ISO */
  targetIso: string
  /** 임계 이하일 때 경고색으로 대비 */
  warnBelowMs?: number
  /** 서버 시각 - 브라우저 시계. 넘기면 남은 시간을 서버 기준으로 센다 */
  offsetMs?: number
  className?: string
}

/**
 * 남은 시간 카운트다운. 시세판/대기방 공용.
 * 마감 임박(임계 이하)이면 경고색(closing-soon)으로 강조.
 */
// 만료를 밖으로 알리지 않는다. 시각이 지났을 때 화면이 무엇을 할지는 목록이 자기 시계로 정하고,
// 여기서 콜백을 부르면 렌더 도중 부수효과가 되어 매 초 다시 불린다
export function Countdown({
  targetIso,
  warnBelowMs = SOFT_CLOSE_THRESHOLD_MS,
  offsetMs = 0,
  className,
}: CountdownProps) {
  const { remaining } = useCountdown(targetIso, 1000, offsetMs)
  const isClosingSoon = remaining > 0 && remaining <= warnBelowMs

  return (
    <span
      className={cn(
        'tabular tracking-tight transition-colors',
        isClosingSoon && 'text-destructive',
        className,
      )}
      role={isClosingSoon ? 'alert' : undefined}
      aria-live={isClosingSoon ? 'assertive' : 'off'}
    >
      {formatDuration(remaining)}
    </span>
  )
}
