import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Bell, CheckCheck, PackageCheck, Trophy, XCircle } from 'lucide-react'
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
import type { AppNotification, NotificationType } from '@/types/domain'
import { MOCK_NOTIFICATIONS } from './mock'

const ICON: Record<NotificationType, LucideIcon> = {
  WON: Trophy,
  EVAL_APPROVED: CheckCheck,
  EVAL_REJECTED: XCircle,
  DEAL_UPDATED: PackageCheck,
}

/** 헤더 알림 드롭다운. 미확인 개수 뱃지 + 목록. */
export function NotificationBell() {
  const [items, setItems] = useState<AppNotification[]>(MOCK_NOTIFICATIONS)
  const unread = items.filter((n) => !n.read).length

  const markAllRead = () =>
    setItems((prev) => prev.map((n) => ({ ...n, read: true })))

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="icon"
          className="relative"
          aria-label={`알림 ${unread}건`}
        >
          <Bell className="size-5" />
          {unread > 0 && (
            <span className="bg-live text-primary-foreground absolute top-1 right-1 flex size-4 items-center justify-center rounded-full text-[10px] font-semibold">
              {unread}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-88 p-0">
        <div className="flex items-center justify-between px-4 py-3">
          <span className="text-sm font-semibold">알림</span>
          {unread > 0 && (
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
          {items.length === 0 ? (
            <p className="text-muted-foreground px-4 py-10 text-center text-sm">
              새로운 알림이 없습니다.
            </p>
          ) : (
            <ul className="divide-border divide-y">
              {items.map((n) => {
                const Icon = ICON[n.type]
                return (
                  <li key={n.id}>
                    <Link
                      to={n.link ?? '#'}
                      className={cn(
                        'hover:bg-accent flex gap-3 px-4 py-3 transition-colors',
                        !n.read && 'bg-accent/40',
                      )}
                    >
                      <span className="bg-muted mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full">
                        <Icon className="size-4" />
                      </span>
                      <span className="min-w-0 flex-1 space-y-0.5">
                        <span className="flex items-center gap-2">
                          <span className="truncate text-sm font-medium">
                            {n.title}
                          </span>
                          {!n.read && (
                            <span className="bg-live size-1.5 shrink-0 rounded-full" />
                          )}
                        </span>
                        <span className="text-muted-foreground line-clamp-2 block text-xs">
                          {n.body}
                        </span>
                        <span className="text-muted-foreground/70 block text-[11px]">
                          {formatRelativeTime(n.createdAt)}
                        </span>
                      </span>
                    </Link>
                  </li>
                )
              })}
            </ul>
          )}
        </ScrollArea>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
