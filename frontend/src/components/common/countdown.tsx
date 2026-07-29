import { useCountdown } from '@/hooks/use-countdown'
import { formatDuration } from '@/lib/format'
import { SOFT_CLOSE_THRESHOLD_MS } from '@/lib/auction'
import { cn } from '@/lib/utils'

interface CountdownProps {
  /** 목표(종료/시작) 시각 ISO */
  targetIso: string
  /** 임계 이하일 때 경고색으로 대비 */
  warnBelowMs?: number
  className?: string
  onElapsed?: () => void
}

/**
 * 남은 시간 카운트다운. 시세판/대기방 공용.
 * 마감 임박(임계 이하)이면 경고색(closing-soon)으로 강조.
 */
export function Countdown({
  targetIso,
  warnBelowMs = SOFT_CLOSE_THRESHOLD_MS,
  className,
  onElapsed,
}: CountdownProps) {
  const { remaining, isElapsed } = useCountdown(targetIso)
  const isClosingSoon = remaining > 0 && remaining <= warnBelowMs

  if (isElapsed) onElapsed?.()

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
