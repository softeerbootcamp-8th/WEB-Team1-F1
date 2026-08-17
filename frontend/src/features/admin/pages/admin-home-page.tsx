import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowRight, RefreshCw, ShieldCheck, Users } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/common/empty-state'
import { useAuth } from '@/features/auth/auth-context'
import { getErrorMessage } from '@/lib/axios'
import { fetchDealerApplications } from '../api'
import { dealerApplicationsQueryKey } from '../query-keys'
import {
  DEALER_APPLICATION_STATUS_LABEL,
  type DealerApplicationStatus,
  type DealerApplicationSummary,
} from '../types'

const TABS: DealerApplicationStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

/**
 * 운영 홈. 딜러 심사 목록을 그대로 첫 화면에 두고, 회원 관리는 링크로 넘긴다.
 * 홈을 탭으로 재구성하지 않은 것은 둘의 성격이 다르기 때문이다 — 심사는 대기 건이 쌓이면 처리하는
 * 할 일 목록이라 첫 화면에 있어야 하고, 회원 관리는 필요할 때 찾아 들어가는 도구다.
 */
export function AdminHomePage() {
  const { user } = useAuth()
  const [status, setStatus] = useState<DealerApplicationStatus>('PENDING')
  const applicationsQuery = useQuery({
    queryKey: dealerApplicationsQueryKey(status),
    queryFn: () => fetchDealerApplications(status),
  })
  const applications = applicationsQuery.data?.applications ?? []

  return (
    <main className="bg-muted/30 min-h-full" aria-label="관리자 홈">
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
          <div className="ml-auto flex gap-2">
            <Button asChild variant="outline">
              <Link to="/admin/users">
                <Users />
                회원 관리
              </Link>
            </Button>
            <Button
              variant="outline"
              onClick={() => void applicationsQuery.refetch()}
              disabled={applicationsQuery.isFetching}
            >
              <RefreshCw className={applicationsQuery.isFetching ? 'animate-spin' : undefined} />
              새로고침
            </Button>
          </div>
        </header>

        <Card className="mt-8">
          <CardHeader className="gap-4">
            <CardTitle>딜러 자격 심사</CardTitle>
            <Tabs value={status} onValueChange={(next) => setStatus(next as DealerApplicationStatus)}>
              <TabsList>
                {TABS.map((tab) => (
                  <TabsTrigger key={tab} value={tab}>
                    {DEALER_APPLICATION_STATUS_LABEL[tab]}
                  </TabsTrigger>
                ))}
              </TabsList>
            </Tabs>
          </CardHeader>
          <CardContent>
            {applicationsQuery.isLoading ? (
              <div className="space-y-3" aria-label="목록 불러오는 중">
                <Skeleton className="h-20 w-full" />
                <Skeleton className="h-20 w-full" />
              </div>
            ) : applicationsQuery.isError ? (
              <EmptyState
                title="신청 목록을 불러오지 못했습니다"
                description={getErrorMessage(applicationsQuery.error, '잠시 후 새로고침해 주세요.')}
              />
            ) : applications.length === 0 ? (
              <EmptyState
                title={`${DEALER_APPLICATION_STATUS_LABEL[status]} 상태의 신청이 없습니다`}
              />
            ) : (
              <ul className="space-y-3">
                {applications.map((application) => (
                  <li key={application.id}>
                    <ApplicationRow application={application} />
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </main>
  )
}

function ApplicationRow({ application }: { application: DealerApplicationSummary }) {
  return (
    <Link
      to={`/admin/dealer-applications/${application.id}`}
      className="border-border hover:bg-accent/50 flex items-center gap-4 rounded-xl border bg-white p-5 transition-colors"
    >
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-semibold">{application.realName}</span>
          <span className="text-muted-foreground text-sm">@{application.username}</span>
          <Badge variant="outline">
            {DEALER_APPLICATION_STATUS_LABEL[application.status]}
          </Badge>
        </div>
        <p className="text-muted-foreground mt-1 text-sm">
          {formatAppliedAt(application.appliedAt)} 신청
        </p>
      </div>
      <ArrowRight className="text-muted-foreground size-5 shrink-0" />
    </Link>
  )
}

/**
 * 서버가 보내는 LocalDateTime에는 시간대가 없다. new Date에 그대로 넘기면 브라우저가 UTC로 읽어
 * 아홉 시간 어긋나므로, 자리만 잘라 그대로 보여준다.
 */
function formatAppliedAt(appliedAt: string): string {
  const [date, time = ''] = appliedAt.split('T')
  return `${date.replaceAll('-', '.')} ${time.slice(0, 5)}`.trim()
}
