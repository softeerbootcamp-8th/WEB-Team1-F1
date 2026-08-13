import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { ClipboardCheck, Gavel, ListChecks, LogOut, User as UserIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
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
import { BrandLogo } from '@/components/common/brand-logo'

const NAV = [
  { to: '/', label: '홈', end: true },
  { to: '/quote', label: '시세 조회' },
  { to: '/auctions', label: '경매 목록' },
  { to: '/sell', label: '내 차 팔기' },
  { to: '/mypage', label: '마이페이지' },
]

const EVALUATOR_NAV = [
  { to: '/', label: '홈', end: true },
  { to: '/evaluations/assignable', label: '배정 대기' },
  { to: '/evaluations/my', label: '내 담당' },
  { to: '/auctions', label: '경매 목록' },
]

export function Header() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [isPastHeroTop, setIsPastHeroTop] = useState(false)
  const navigation = user?.role === 'EVALUATOR' ? EVALUATOR_NAV : NAV
  const isHome = pathname === '/'
  const hasHomeHero = isHome
  const isHomeOverlay = hasHomeHero && !isPastHeroTop

  useEffect(() => {
    if (!hasHomeHero) {
      setIsPastHeroTop(false)
      return
    }

    const updateHeader = () => setIsPastHeroTop(window.scrollY > 32)

    updateHeader()
    window.addEventListener('scroll', updateHeader, { passive: true })

    return () => window.removeEventListener('scroll', updateHeader)
  }, [hasHomeHero])

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      navigate('/', { replace: true })
    }
  }

  return (
    <header
      className={cn(
        'top-0 z-40 w-full transition-[background-color,border-color,box-shadow] duration-500',
        hasHomeHero ? 'fixed' : 'bg-background/95 sticky border-b',
        isHomeOverlay
          ? 'border-transparent bg-transparent text-white'
          : 'bg-background/95 text-foreground shadow-[0_1px_0_rgb(0_0_0/0.08)]',
      )}
    >
      <div className="mx-auto flex h-(--spacing-header) max-w-7xl items-center gap-8 px-6">
        {/* 로고 */}
        <Link to="/" className="flex items-center" aria-label="RACE 홈으로">
          <BrandLogo variant={isHomeOverlay ? 'white' : 'black'} className="h-9" />
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
                  'rounded-md px-3 py-2 text-base font-medium transition-colors',
                  isHomeOverlay
                    ? isActive
                      ? 'text-white'
                      : 'text-white/65 hover:text-white'
                    : isActive
                      ? 'text-foreground'
                      : 'text-muted-foreground hover:text-foreground',
                )
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="ml-auto flex items-center gap-1">
          <NotificationBell tone={isHomeOverlay ? 'inverse' : 'default'} />

          {isAuthenticated && user ? (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  className={cn(
                    isHomeOverlay &&
                      'bg-black/30 text-white transition-none hover:bg-black/45 hover:text-white',
                  )}
                  aria-label="내 계정"
                >
                  {/* 이름을 좁은 화면에서도 숨기지 않는다. 아바타가 없어 숨기면 빈 버튼만 남는다 */}
                  <span className="text-sm font-medium">{user.realName}</span>
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
                    <DropdownMenuItem onClick={() => navigate('/auctions')}>
                      <Gavel className="size-4" />
                      경매 목록
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                  </>
                )}
                {user.role !== 'EVALUATOR' && (
                  <>
                    <DropdownMenuItem onClick={() => navigate('/mypage')}>
                      <UserIcon className="size-4" />
                      마이페이지
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                  </>
                )}
                <DropdownMenuItem variant="destructive" onClick={handleLogout}>
                  <LogOut className="size-4" />
                  로그아웃
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          ) : (
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="sm"
                className={cn(
                  'transition-none',
                  isHomeOverlay &&
                    'bg-black/30 text-white hover:bg-black/45 hover:text-white',
                )}
                asChild
              >
                <Link to="/login">로그인</Link>
              </Button>
              <Button
                size="sm"
                className={cn(
                  'transition-none',
                  isHomeOverlay &&
                    'bg-white text-black hover:bg-white/85 hover:text-black',
                )}
                asChild
              >
                <Link to="/signup">회원가입</Link>
              </Button>
            </div>
          )}
        </div>
      </div>
    </header>
  )
}
