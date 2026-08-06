import { Link, NavLink, useNavigate } from 'react-router-dom'
import { ClipboardCheck, ListChecks, LogOut, User as UserIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  Avatar,
  AvatarFallback,
} from '@/components/ui/avatar'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import { NotificationBell } from '@/features/notifications/notification-bell'
import { ROLE_LABEL, useAuth } from '@/features/auth/auth-context'

const NAV = [
  { to: '/', label: '홈', end: true },
  { to: '/quote', label: '시세조회' },
  { to: '/auctions', label: '경매 목록' },
  { to: '/sell', label: '내차 팔기' },
  { to: '/mypage', label: '마이페이지' },
]

const EVALUATOR_NAV = [
  { to: '/', label: '홈', end: true },
  { to: '/evaluations/assignable', label: '배정 대기' },
  { to: '/evaluations/my', label: '내 담당' },
]

export function Header() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const navigation = user?.role === 'EVALUATOR' ? EVALUATOR_NAV : NAV

  return (
    <header className="bg-background/80 supports-[backdrop-filter]:bg-background/70 sticky top-0 z-40 border-b backdrop-blur">
      <div className="mx-auto flex h-(--spacing-header) max-w-7xl items-center gap-8 px-6">
        {/* 로고 */}
        <Link to="/" className="flex items-center gap-2" aria-label="RACE 홈으로">
          <span className="text-2xl font-bold tracking-[0.06em]">RACE</span>
        </Link>

        {/* 데스크톱 내비게이션 */}
        <nav className="hidden items-center gap-1 md:flex" aria-label="주요 메뉴">
          {navigation.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  'hover:text-foreground rounded-md px-3 py-2 text-base font-medium transition-colors',
                  isActive ? 'text-foreground' : 'text-muted-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-1">
          <NotificationBell />

          {isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  className="gap-2 pl-2"
                  aria-label="내 계정"
                >
                  <Avatar className="size-7">
                    <AvatarFallback>{user.realName.slice(0, 1)}</AvatarFallback>
                  </Avatar>
                  <span className="hidden text-sm font-medium sm:inline">
                    {user.realName}
                  </span>
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-52">
                <DropdownMenuLabel className="flex items-center justify-between gap-2">
                  <span className="truncate">{user.realName}</span>
                  <Badge variant="outline" className="shrink-0">
                    {ROLE_LABEL[user.role]}
                  </Badge>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                {user.role === 'EVALUATOR' && (
                  <>
                    <DropdownMenuItem onClick={() => navigate('/evaluations/assignable')}>
                      <ListChecks className="size-4" />
                      배정 대기 목록
                    </DropdownMenuItem>
                    <DropdownMenuItem onClick={() => navigate('/evaluations/my')}>
                      <ClipboardCheck className="size-4" />
                      내 담당 목록
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                  </>
                )}
                <DropdownMenuItem onClick={() => navigate('/mypage')}>
                  <UserIcon className="size-4" />
                  마이페이지
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onClick={logout}>
                  <LogOut className="size-4" />
                  로그아웃
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="flex items-center gap-2">
              <Button variant="ghost" size="sm" asChild>
                <Link to="/login">로그인</Link>
              </Button>
              <Button size="sm" asChild>
                <Link to="/signup">회원가입</Link>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
