import { Check } from 'lucide-react'

import { cn } from '@/lib/utils'
import type { EvaluationStatus } from '../types'

const EVALUATION_STEPS = ['배정 대기', '평가 진행 중', '차량 진단 완료'] as const

/**
 * 판매자와 평가사가 같은 진행 단계를 본다. 정보와 버튼이 섞이지 않도록
 * 상태는 면이 있는 배지 대신 점과 선으로만 표현한다.
 *
 * <b>반려는 여기서 그리지 않는다.</b> 반려는 이 선 위의 한 지점이 아니라 선을 벗어난 결말이라,
 * 같은 자리에 그리면 세 단계가 있던 곳에 한 줄짜리 다른 모양이 들어앉는다. 끝난 사실을 알리는
 * 것은 카드 머리의 배지가 맡는다 — 경매 상태를 알리는 배지와 같은 자리, 같은 모양이다.
 */
export function EvaluationProgress({
  status,
  assigned,
}: {
  status: Exclude<EvaluationStatus, 'REJECTED'>
  assigned: boolean
}) {
  const current = status === 'APPROVED' ? 2 : assigned ? 1 : 0

  return (
    <ol className="grid grid-cols-3" aria-label="방문견적 진행 단계">
      {EVALUATION_STEPS.map((step, index) => {
        const completed = index < current || (status === 'APPROVED' && index === current)

        return (
          <li
            key={step}
            className="relative flex min-w-0 flex-col items-center gap-2"
          >
          {index < EVALUATION_STEPS.length - 1 && (
            <span
              aria-hidden
              className={cn(
                'absolute top-2.5 left-[calc(50%+0.75rem)] h-px w-[calc(100%-1.5rem)]',
                index < current ? 'bg-primary' : 'bg-border',
              )}
            />
          )}
          <span
            data-slot="evaluation-step-marker"
            className={cn(
              'relative z-10 flex size-5 items-center justify-center rounded-full border-2 bg-background',
              index <= current ? 'border-primary' : 'border-border',
              index === current && !completed && 'after:bg-primary after:size-2 after:rounded-full',
              completed && 'bg-primary text-primary-foreground',
            )}
          >
            {completed && <Check className="size-3" strokeWidth={3} />}
          </span>
          <span
            data-slot="evaluation-step"
            aria-current={index === current ? 'step' : undefined}
            className={cn(
              'whitespace-nowrap text-center text-xs sm:text-sm',
              index === current ? 'text-foreground font-bold' : 'text-muted-foreground font-medium',
            )}
          >
            {step}
          </span>
          </li>
        )
      })}
    </ol>
  )
}
