import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { ArrowRight, RefreshCw } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { Tabs, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { EmptyState } from '@/components/common/empty-state'
import { getErrorMessage } from '@/lib/axios'
import { fetchDealerApplications } from '../api'
import { formatServerDateTime } from '../format'
import { dealerApplicationsQueryKey } from '../query-keys'
import {
  DEALER_APPLICATION_STATUS_LABEL,
  type DealerApplicationStatus,
  type DealerApplicationSummary,
} from '../types'

const STATUS_TABS: DealerApplicationStatus[] = ['PENDING', 'APPROVED', 'REJECTED']

/**
 * 딜러 자격 심사 목록. 상태별로 나눠 접수 순으로 보여주고, 한 건을 누르면 상세로 넘어간다.
 * <p>
 * 새로고침을 이 안에 둔다. 운영 화면의 머리글은 두 탭이 함께 쓰는 자리라, 거기 두면 지금 보이지도
 * 않는 목록을 다시 읽는 버튼이 된다.
 */
export function DealerApplicationsPanel() {
  const [status, setStatus] = useState<DealerApplicationStatus>('PENDING')
  const applicationsQuery = useQuery({
    queryKey: dealerApplicationsQueryKey(status),
    queryFn: () => fetchDealerApplications(status),
  })
  const applications = applicationsQuery.data?.applications ?? []

  return (
    <Card>
      <CardHeader className="gap-4">
        <div className="flex flex-wrap items-center gap-3">
          <CardTitle>딜러 자격 심사</CardTitle>
          <Button
            variant="outline"
            size="sm"
            className="ml-auto"
            onClick={() => void applicationsQuery.refetch()}
            disabled={applicationsQuery.isFetching}
          >
            <RefreshCw className={applicationsQuery.isFetching ? 'animate-spin' : undefined} />
            새로고침
          </Button>
        </div>
        <Tabs value={status} onValueChange={(next) => setStatus(next as DealerApplicationStatus)}>
          <TabsList>
            {STATUS_TABS.map((tab) => (
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
          <EmptyState title={`${DEALER_APPLICATION_STATUS_LABEL[status]} 상태의 신청이 없습니다`} />
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
          <Badge variant="outline">{DEALER_APPLICATION_STATUS_LABEL[application.status]}</Badge>
        </div>
        <p className="text-muted-foreground mt-1 text-sm">
          {formatServerDateTime(application.appliedAt)} 신청
        </p>
      </div>
      <ArrowRight className="text-muted-foreground size-5 shrink-0" />
    </Link>
  )
}
