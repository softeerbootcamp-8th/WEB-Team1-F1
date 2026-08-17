import { useLocation, useNavigate } from 'react-router-dom'
import { ShieldCheck } from 'lucide-react'

import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { useAuth } from '@/features/auth/auth-context'
import { DealerApplicationsPanel } from '../components/dealer-applications-panel'
import { UsersPanel } from '../components/users-panel'

/**
 * 탭이 곧 주소다. `/admin/users` 처럼 경로에 실어야 딜러 심사 상세에서 돌아왔을 때 보던 탭이
 * 유지되고, 관리자에게 특정 탭을 링크로 건네줄 수도 있다({@code MyPage}와 같은 규칙이다).
 *
 * 심사 목록은 `/admin` 그대로 둔다. 관리자의 첫 화면이고({@code RoleHome}), 상세 화면과
 * 관리자 전용 라우트가 모두 이 주소로 되돌아온다.
 */
const TABS = [
  { value: 'dealer-applications', label: '딜러 자격 심사', path: '/admin' },
  { value: 'users', label: '회원 관리', path: '/admin/users' },
] as const

type Tab = (typeof TABS)[number]['value']

/** 운영 관리. 딜러 심사와 회원 관리를 탭으로 나눠 담는 껍데기다. */
export function AdminHomePage() {
  const { user } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()

  // 모르는 경로가 들어와도 첫 탭으로 떨어뜨린다. `/admin` 자체가 그 경우다
  const tab: Tab = pathname.split('/')[2] === 'users' ? 'users' : 'dealer-applications'

  return (
    <main className="bg-muted/30 min-h-full" aria-label="운영 관리">
      <div className="mx-auto max-w-5xl px-6 py-12">
        <header className="flex flex-wrap items-center gap-3">
          <span className="bg-foreground text-background flex size-11 items-center justify-center rounded-2xl">
            <ShieldCheck className="size-5" />
          </span>
          <div className="min-w-0">
            <h1 className="text-2xl font-semibold tracking-tight">운영 관리</h1>
            <p className="text-muted-foreground mt-0.5 text-sm">
              {user?.realName} 관리자님, 환영합니다
            </p>
          </div>
        </header>

        <Tabs
          className="mt-8"
          value={tab}
          onValueChange={(next) => {
            const target = TABS.find((candidate) => candidate.value === next)
            // replace 로 옮긴다. 탭을 오가는 것이 뒤로 가기 이력에 쌓이면 운영 화면을 벗어나려고
            // 뒤로 가기를 여러 번 눌러야 한다(MyPage 와 같은 판단이다)
            if (target) navigate(target.path, { replace: true })
          }}
        >
          <TabsList>
            {TABS.map((candidate) => (
              <TabsTrigger key={candidate.value} value={candidate.value}>
                {candidate.label}
              </TabsTrigger>
            ))}
          </TabsList>

          {/*
            * 보이지 않는 탭의 패널은 그리지 않는다. Radix 는 기본적으로 선택되지 않은 TabsContent 를
            * 마운트하지 않으므로, 회원 목록 조회가 심사 탭에서 미리 나가는 일이 없다
            */}
          <TabsContent value="dealer-applications" className="mt-6">
            <DealerApplicationsPanel />
          </TabsContent>

          <TabsContent value="users" className="mt-6">
            <UsersPanel />
          </TabsContent>
        </Tabs>
      </div>
    </main>
  )
}
