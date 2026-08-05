import { useState } from 'react'
import {
  Bell,
  CheckCheck,
  CircleOff,
  CircleX,
  Gavel,
  HandCoins,
  PackageCheck,
  PartyPopper,
  Trophy,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import { formatRelativeTime } from '@/lib/format'
import { useAuth } from '@/features/auth/auth-context'
import type { NotificationType } from '@/types/domain'
import { useNotifications } from './use-notifications'

/**
 * 종류별 아이콘. 전수 대응표라서 백엔드에 종류가 늘고 타입에 반영되면 여기서 빌드가 깨진다.
 * 기본 아이콘으로 덮으면 새 종류가 아무 표시 없이 조용히 나가므로, 규칙이 아니라 타입으로 막는다.
 */
const ICON: Record<NotificationType, LucideIcon> = {
  WELCOME: PartyPopper,
  EVAL_APPROVED: CheckCheck,
  EVAL_REJECTED: CircleX,
  AUCTION_WON: Trophy,
  AUCTION_WON_RESULT: Trophy,
  AUCTION_ENDED: Gavel,
  AUCTION_SOLD: HandCoins,
  AUCTION_FAILED: CircleOff,
  DEAL_STATUS_CHANGED: PackageCheck,
}

// 배지에 그릴 수 있는 최대 숫자. 넘으면 폭이 흔들려 헤더 정렬이 밀린다
const MAX_BADGE_COUNT = 99

/** 헤더 알림 드롭다운. 안 읽은 건수 배지 + 목록, 누르면 읽음 처리하고 해당 화면으로 이동한다. */
export function NotificationBell() {
  const { isAuthenticated } = useAuth()
  const { items, unreadCount, hasNext, isLoading, isLoadingMore, loadMore, markAllRead, open } =
    useNotifications()
  const [isOpen, setIsOpen] = useState(false)

  // 로그인하지 않으면 볼 알림도, 열어 둘 연결도 없다
  if (!isAuthenticated) return null

  const badge = unreadCount > MAX_BADGE_COUNT ? `${MAX_BADGE_COUNT}+` : unreadCount

  return (
    <DropdownMenu open={isOpen} onOpenChange={setIsOpen}>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={`알림 ${unreadCount}건`}
        >
          <Bell className="size-5" aria-hidden />
          {unreadCount > 0 && (
            <span className="bg-live text-primary-foreground tabular absolute top-0.5 right-0.5 flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold">
              {badge}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-88 p-0">
        <div className="flex items-center justify-between px-4 py-3">
          <span className="text-sm font-semibold">알림</span>
          {unreadCount > 0 && (
            <button
              type="button"
              onClick={markAllRead}
              className="text-muted-foreground hover:text-foreground text-xs"
            >
              모두 읽음
            </button>
          )}
        </div>

        <ScrollArea className="max-h-96">
          {isLoading && items.length === 0 ? (
            <p className="text-muted-foreground px-4 py-10 text-center text-sm">
              알림을 불러오는 중입니다.
            </p>
          ) : items.length === 0 ? (
            <p className="text-muted-foreground px-4 py-10 text-center text-sm">
              새로운 알림이 없습니다.
            </p>
          ) : (
            <>
              <ul className="divide-border divide-y">
                {items.map((notification) => {
                  const Icon = ICON[notification.type]

                  return (
                    <li key={notification.id}>
                      <button
                        type="button"
                        onClick={() => {
                          setIsOpen(false)
                          open(notification)
                        }}
                        className={cn(
                          'hover:bg-accent flex w-full gap-3 px-4 py-3 text-left transition-colors',
                          !notification.read && 'bg-accent/40',
                        )}
                      >
                        <span className="bg-muted mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full">
                          <Icon className="size-4" aria-hidden />
                        </span>
                        <span className="min-w-0 flex-1 space-y-0.5">
                          <span className="flex items-start gap-2">
                            <span className="flex-1 text-sm font-medium">
                              {notification.message}
                            </span>
                            {!notification.read && (
                              <span className="bg-live mt-1.5 size-1.5 shrink-0 rounded-full" />
                            )}
                          </span>
                          <span className="text-muted-foreground/70 block text-[11px]">
                            {formatRelativeTime(notification.createdAt)}
                          </span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>

              {hasNext && (
                <div className="p-2">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="w-full"
                    disabled={isLoadingMore}
                    onClick={() => void loadMore()}
                  >
                    {isLoadingMore ? '불러오는 중…' : '더 보기'}
                  </Button>
                </div>
              )}
            </>
          )}
        </ScrollArea>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
