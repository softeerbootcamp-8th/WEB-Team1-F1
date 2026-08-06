import { CalendarClock, Flag, Gavel, Lock, Trophy } from 'lucide-react'

import { cn } from '@/lib/utils'

/** 경매방이 지금 어느 단계인지, 화면마다 색과 문구가 다르다 */
export type RoomStateMode = 'NOT_OPEN' | 'WAITING' | 'LIVE' | 'RESULT' | 'CLOSED'

const MODE_META: Record<
  RoomStateMode,
  { label: string; hint: string; icon: typeof Lock; className: string; barClassName: string }
> = {
  NOT_OPEN: {
    label: '입장 전',
    hint: '입장 가능 시각이 되면 자동으로 들어갑니다',
    icon: Lock,
    className: 'border-dashed bg-muted text-muted-foreground',
    barClassName: 'bg-muted-foreground/40',
  },
  WAITING: {
    label: '대기중',
    hint: '시작 시각이 되면 입찰이 열립니다',
    icon: CalendarClock,
    className: 'bg-muted text-foreground',
    barClassName: 'bg-muted-foreground',
  },
  LIVE: {
    label: '입찰 진행중',
    hint: '마감 30초 전 입찰은 마감을 연장합니다',
    icon: Gavel,
    className: 'border-status-live/40 bg-status-live/10 text-status-live',
    barClassName: 'bg-status-live',
  },
  RESULT: {
    label: '방금 마감',
    hint: '결과를 확인할 수 있는 구간입니다',
    icon: Trophy,
    className: 'border-price-up/40 bg-price-up/10 text-price-up',
    barClassName: 'bg-price-up',
  },
  CLOSED: {
    label: '종료된 경매',
    hint: '더 이상 바뀌지 않는 결과입니다',
    icon: Flag,
    className: 'bg-foreground text-background',
    barClassName: 'bg-foreground',
  },
}

/** 제목 왼쪽에 세우는 상태색 막대, 제목과 상태를 한 덩어리로 읽히게 한다 */
export function RoomStateBar({ mode }: { mode: RoomStateMode }) {
  return (
    <span
      className={cn('w-1 shrink-0 self-stretch rounded-full', MODE_META[mode].barClassName)}
      aria-hidden
    />
  )
}

/** 지금 단계를 이름과 한 줄 설명으로 알리는 띠 */
export function RoomStateBanner({ mode }: { mode: RoomStateMode }) {
  const meta = MODE_META[mode]
  const Icon = meta.icon

  return (
    <div
      className={cn(
        'flex flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border px-4 py-3',
        meta.className,
      )}
    >
      {mode === 'LIVE' ? (
        <span
          className="bg-status-live size-2 rounded-full"
          style={{ animation: 'var(--animate-live-pulse)' }}
          aria-hidden
        />
      ) : (
        <Icon className="size-4" aria-hidden />
      )}
      <span className="text-sm font-semibold">{meta.label}</span>
      {/* 좁은 화면에서는 제목과 한 줄에 들어가야 해서 설명은 접는다 */}
      <span className="hidden text-xs opacity-80 sm:inline">{meta.hint}</span>
    </div>
  )
}
