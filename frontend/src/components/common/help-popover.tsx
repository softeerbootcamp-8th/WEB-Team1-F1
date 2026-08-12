import { useRef, useState, type ReactNode } from 'react'
import { HelpCircle } from 'lucide-react'

import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { cn } from '@/lib/utils'

/** 마우스를 콘텐츠로 옮기는 사이 닫히지 않게 두는 유예 */
const CLOSE_DELAY_MS = 120

interface HelpPopoverProps {
  /** 아이콘만 있는 버튼이라 접근 이름을 반드시 받는다 */
  label: string
  children: ReactNode
  /** 기본은 아래. 입력 상자를 가리는 자리면 옆으로 뺀다 */
  side?: 'top' | 'right' | 'bottom' | 'left'
  align?: 'start' | 'center' | 'end'
  /**
   * 여는 방향과 직각으로 밀어내는 거리.
   * 기준이 버튼(32px)이라 옆으로 열면 상자 위쪽에 치우친다 — 상자와 상하 대칭으로 두려면
   * 부르는 쪽이 상자 높이를 재서 그 절반만큼 내린다
   */
  alignOffset?: number
  className?: string
  contentClassName?: string
}

/**
 * 도움말 버튼. 마우스로는 올리면 열리고, 키보드로는 초점이 오면 열리며, 터치에서는 눌러서 연다.
 *
 * 툴팁이 아니라 팝오버를 쓴다. 담는 것이 표라 `role="tooltip"`으로 두면 낭독기가 한 덩어리
 * 설명으로 읽어 셀 단위 탐색이 되지 않는다. Esc·바깥 클릭 닫기는 팝오버가 이미 갖고 있다.
 *
 * 콘텐츠는 열려 있을 때만 마운트된다. 안에서 데이터를 받아 오는 도움말이 "열 때 요청"이 되는 것이
 * 이 성질에 기대고 있어, 닫힌 상태에서도 렌더되도록 바꾸면 요청 시점이 함께 바뀐다.
 */
export function HelpPopover({
  label,
  children,
  side = 'bottom',
  align = 'end',
  alignOffset = 0,
  className,
  contentClassName,
}: HelpPopoverProps) {
  const [open, setOpen] = useState(false)
  const closeTimer = useRef<number | null>(null)
  // hover 로 열렸을 때 콘텐츠로 초점을 뺏지 않기 위해 무엇이 열었는지 기억한다
  const openedByHover = useRef(false)
  // Esc·바깥 누르기로 닫았는데 커서가 아직 버튼 위에 있는 상태.
  // 커서를 치우기 전까지는 hover 로 되열지 않는다 — 닫은 것이 사용자의 의사표시라,
  // 곧바로 다시 열리면 Esc 가 듣지 않는 것처럼 보인다
  const hoverSuppressed = useRef(false)

  const cancelClose = () => {
    if (closeTimer.current !== null) {
      window.clearTimeout(closeTimer.current)
      closeTimer.current = null
    }
  }

  const scheduleClose = () => {
    cancelClose()
    closeTimer.current = window.setTimeout(() => setOpen(false), CLOSE_DELAY_MS)
  }

  return (
    <Popover
      open={open}
      onOpenChange={(next) => {
        cancelClose()
        // 클릭·Esc·바깥 누르기로 바뀐 경우다. 이때는 초점을 콘텐츠로 옮겨 준다
        openedByHover.current = false
        // 닫은 것이 사용자의 의사표시라, 커서를 치우기 전까지 hover 로 되열지 않는다
        if (!next) hoverSuppressed.current = true
        setOpen(next)
      }}
    >
      <PopoverTrigger
        type="button"
        aria-label={label}
        className={cn(
          'text-muted-foreground hover:text-foreground focus-visible:ring-ring inline-flex size-8 items-center justify-center rounded-full transition-colors focus-visible:ring-2 focus-visible:outline-none',
          className,
        )}
        onPointerEnter={(event) => {
          // 터치는 pointerenter 도 함께 오지만 눌러서 여는 경로가 따로 있어 여기서는 무시한다
          if (event.pointerType !== 'mouse') return
          if (hoverSuppressed.current) return
          cancelClose()
          openedByHover.current = true
          setOpen(true)
        }}
        onPointerLeave={(event) => {
          if (event.pointerType !== 'mouse') return
          // 커서가 떠났으니 다시 hover 로 열 수 있다
          hoverSuppressed.current = false
          scheduleClose()
        }}
        onFocus={(event) => {
          // 클릭으로 들어온 초점까지 열면 클릭이 토글로 동작하지 않는다.
          // :focus-visible 이 키보드로 옮긴 초점만 걸러 준다
          if (!event.target.matches(':focus-visible')) return
          openedByHover.current = false
          setOpen(true)
        }}
      >
        <HelpCircle className="size-5" aria-hidden />
      </PopoverTrigger>
      <PopoverContent
        side={side}
        align={align}
        alignOffset={alignOffset}
        className={contentClassName}
        onOpenAutoFocus={(event) => {
          if (openedByHover.current) event.preventDefault()
        }}
        onPointerEnter={cancelClose}
        onPointerLeave={scheduleClose}
      >
        {children}
      </PopoverContent>
    </Popover>
  )
}
