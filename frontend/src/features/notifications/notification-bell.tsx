import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ChevronRight } from 'lucide-react'

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Button } from '@/components/ui/button'
import { ScrollArea } from '@/components/ui/scroll-area'
import { cn } from '@/lib/utils'
import { formatRelativeTime } from '@/lib/format'
import { useAuth } from '@/features/auth/auth-context'
import { BellIcon } from './bell-icon'
import { NOTIFICATION_ICON } from './notification-icon'
import { useNotifications } from './use-notifications'

// 배지에 그릴 수 있는 최대 숫자. 넘으면 폭이 흔들려 헤더 정렬이 밀린다
const MAX_BADGE_COUNT = 99

/** 헤더 알림 드롭다운. 안 읽은 건수 배지 + 목록, 행을 누르면 읽음 처리하고 그 화면으로 이동한다. */
export function NotificationBell({
  tone = 'default',
}: {
  tone?: 'default' | 'inverse'
}) {
  const { isAuthenticated } = useAuth()
  const { items, unreadCount, hasNext, isLoading, isLoadingMore, loadMore, markAllRead, markRead } =
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
          className={cn(
            'relative',
            tone === 'inverse' &&
              'bg-black/30 text-white transition-none hover:bg-black/45 hover:text-white',
          )}
          aria-label={`알림 ${unreadCount}건`}
        >
          <BellIcon className="size-5" />
          {unreadCount > 0 && (
            <span className="bg-live text-primary-foreground tabular absolute top-0.5 right-0.5 flex min-w-4 items-center justify-center rounded-full px-1 text-[10px] font-semibold">
              {badge}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>

      <DropdownMenuContent align="end" className="w-[min(28rem,calc(100vw-2rem))] p-0">
        <div className="flex items-center justify-between px-5 py-4">
          <span className="text-base font-semibold">알림</span>
          {unreadCount > 0 && (
            <button
              type="button"
              onClick={markAllRead}
              className="text-muted-foreground hover:text-foreground text-sm"
            >
              모두 읽음
            </button>
          )}
        </div>

        <ScrollArea viewportClassName="max-h-[32rem]">
          {isLoading && items.length === 0 ? (
            <p className="text-muted-foreground px-5 py-12 text-center text-sm">
              알림을 불러오는 중입니다.
            </p>
          ) : items.length === 0 ? (
            <p className="text-muted-foreground px-5 py-12 text-center text-sm">
              새로운 알림이 없습니다.
            </p>
          ) : (
            <>
              <ul className="divide-border divide-y">
                {items.map((notification) => {
                  const Icon = NOTIFICATION_ICON[notification.type]

                  return (
                    <li key={notification.id}>
                      {/*
                        버튼이 아니라 링크다. 이동이 목적이면 링크여야 브라우저가 목적지를 상태바에
                        보여 주고, 새 탭으로도 열 수 있고, 스크린리더가 "링크"로 읽는다.
                        읽음 처리는 이동에 딸린 부수 효과라 onClick 에 둔다
                      */}
                      <DropdownMenuItem asChild>
                        <Link
                          to={notification.link}
                          onClick={() => {
                            setIsOpen(false)
                            markRead(notification)
                          }}
                          className={cn(
                            'hover:bg-accent group flex cursor-pointer gap-3.5 rounded-none px-5 py-4 transition-colors',
                            !notification.read && 'bg-accent/40',
                          )}
                        >
                          <span className="bg-muted mt-0.5 flex size-10 shrink-0 items-center justify-center rounded-full">
                            <Icon className="size-5" aria-hidden />
                          </span>

                          <span className="min-w-0 flex-1 space-y-1">
                            <span className="flex items-start gap-2">
                              <span className="flex-1 text-[0.9375rem] leading-snug font-medium">
                                {notification.message}
                              </span>
                              {!notification.read && (
                                <span className="bg-live mt-1.5 size-2 shrink-0 rounded-full" />
                              )}
                            </span>
                            <span className="text-muted-foreground/70 block text-xs">
                              {formatRelativeTime(notification.createdAt)}
                            </span>
                          </span>

                          {/* 눌렀을 때 어디로 간다는 신호. 호버에서 살짝 밀려 방향감을 준다 */}
                          <ChevronRight
                            className="text-muted-foreground/40 group-hover:text-muted-foreground mt-3 size-4 shrink-0 transition-all group-hover:translate-x-0.5"
                            aria-hidden
                          />
                        </Link>
                      </DropdownMenuItem>
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
