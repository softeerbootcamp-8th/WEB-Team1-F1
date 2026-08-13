import { useEffect, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom'
import { ClipboardCheck, Gavel, ListChecks, LogOut, Menu, User as UserIcon } from 'lucide-react'

import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
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
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)
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
      setIsMobileMenuOpen(false)
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
      <div className="mx-auto flex h-(--spacing-header) max-w-7xl items-center gap-3 px-4 sm:px-6 lg:gap-8">
        {/* 모바일에서는 사라진 메뉴 대신 왼쪽 진입점을 둔다. 필터는 화면 로컬 기능이라 여기 넣지 않는다. */}
        <Dialog open={isMobileMenuOpen} onOpenChange={setIsMobileMenuOpen}>
          <DialogTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className={cn(
                'lg:hidden',
                isHomeOverlay &&
                  'bg-black/30 text-white transition-none hover:bg-black/45 hover:text-white',
              )}
              aria-label="주요 메뉴 열기"
            >
              <Menu className="size-5" />
            </Button>
          </DialogTrigger>

          <DialogContent
            className="top-0 left-0 flex h-dvh w-[min(20rem,calc(100vw-1rem))] max-w-none translate-x-0 translate-y-0 flex-col gap-0 rounded-none border-y-0 border-l-0 p-0 data-[state=closed]:slide-out-to-left data-[state=closed]:zoom-out-100 data-[state=open]:slide-in-from-left data-[state=open]:zoom-in-100 sm:max-w-none"
          >
            <DialogHeader className="border-b px-6 py-5 pr-14 text-left">
              <DialogTitle>메뉴</DialogTitle>
              <DialogDescription>
                {user ? `${user.realName} · ${ROLE_LABEL[user.role]}` : 'RACE 주요 메뉴'}
              </DialogDescription>
            </DialogHeader>

            <nav className="flex-1 overflow-y-auto p-3" aria-label="모바일 주요 메뉴">
              {navigation.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={() => setIsMobileMenuOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      'block rounded-lg px-4 py-3 text-base font-medium transition-colors',
                      isActive
                        ? 'bg-accent text-foreground'
                        : 'text-muted-foreground hover:bg-accent/60 hover:text-foreground',
                    )
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>

            <div className="border-t p-4">
              {isAuthenticated ? (
                <Button variant="outline" className="w-full justify-start" onClick={handleLogout}>
                  <LogOut className="size-4" />
                  로그아웃
                </Button>
              ) : (
                <div className="grid grid-cols-2 gap-2">
                  <Button variant="outline" asChild>
                    <Link to="/login" onClick={() => setIsMobileMenuOpen(false)}>
                      로그인
                    </Link>
                  </Button>
                  <Button asChild>
                    <Link to="/signup" onClick={() => setIsMobileMenuOpen(false)}>
                      회원가입
                    </Link>
                  </Button>
                </div>
              )}
            </div>
          </DialogContent>
        </Dialog>

        {/* 로고 */}
        <Link to="/" className="flex min-w-0 items-center" aria-label="RACE 홈으로">
          <BrandLogo variant={isHomeOverlay ? 'white' : 'black'} className="h-8 sm:h-9" />
        </Link>

        {/* 데스크톱 내비게이션 */}
        <nav className="hidden items-center gap-1 lg:flex" aria-label="주요 메뉴">
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
            <div className="hidden lg:block">
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
                    <span className="max-w-28 truncate text-sm font-medium">{user.realName}</span>
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
            </div>
          ) : (
            <div className="hidden items-center gap-2 lg:flex">
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
