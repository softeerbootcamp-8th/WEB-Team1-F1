import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  CheckCircle2,
  ClipboardCheck,
  ListChecks,
  RefreshCw,
  XCircle,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { Button } from '@/components/ui/button'
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { CinematicCarBackdrop } from '@/components/common/cinematic-car-backdrop'
import { useAuth } from '@/features/auth/auth-context'
import { getErrorMessage } from '@/lib/axios'
import { fetchAssignableEvaluationCount, fetchMyAssignments } from '../api'
import { summarizeEvaluatorHome } from '../evaluator-home-summary'
import {
  ASSIGNABLE_EVALUATIONS_COUNT_QUERY_KEY,
  MY_ASSIGNMENTS_QUERY_KEY,
} from '../query-keys'

export function EvaluatorHomePage() {
  const { user } = useAuth()
  const assignableQuery = useQuery({
    // 목록이 아니라 건수만 읽는다. 목록은 나누어 나가므로 첫 페이지 길이로는 전체를 셀 수 없고,
    // 홈은 애초에 카드가 아니라 수만 보여준다
    queryKey: ASSIGNABLE_EVALUATIONS_COUNT_QUERY_KEY,
    queryFn: fetchAssignableEvaluationCount,
  })
  const assignmentsQuery = useQuery({
    queryKey: MY_ASSIGNMENTS_QUERY_KEY,
    queryFn: fetchMyAssignments,
  })
  const isError = assignableQuery.isError || assignmentsQuery.isError
  const summary = summarizeEvaluatorHome(
    assignableQuery.data?.count ?? 0,
    assignmentsQuery.data?.evaluations ?? [],
  )

  const refetchAll = () => {
    void Promise.all([assignableQuery.refetch(), assignmentsQuery.refetch()])
  }

  return (
    <main className="bg-muted/30 min-h-full" aria-label="평가사 홈">
      <section className="relative isolate min-h-[max(680px,100svh)] overflow-hidden bg-[#080a0b] text-white">
        <CinematicCarBackdrop
          className="home-hero-media -z-20"
          imageClassName="home-hero-image object-[center_68%] opacity-75 md:object-[center_62%] lg:object-center lg:opacity-85"
          sizes="100vw"
        />
        <div className="absolute inset-0 -z-10 bg-linear-to-r from-black/75 via-black/20 to-black/30" />
        <div className="absolute inset-0 -z-10 bg-linear-to-t from-black/90 via-black/20 to-black/20" />

        <div className="mx-auto flex min-h-[max(680px,100svh)] max-w-7xl flex-col justify-between px-6 pt-28 pb-24 lg:pb-28">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <h1 className="text-2xl font-semibold tracking-tight text-white md:text-3xl">
              {user?.realName} 평가사님, 환영합니다
            </h1>
            <Button
              size="lg"
              variant="outline"
              className="h-12 border-white/25 bg-black/35 px-5 text-base text-white backdrop-blur-md hover:bg-black/50 hover:text-white"
              onClick={refetchAll}
              disabled={assignableQuery.isFetching || assignmentsQuery.isFetching}
            >
              <RefreshCw
                className={`size-5 ${
                  assignableQuery.isFetching || assignmentsQuery.isFetching
                    ? 'animate-spin'
                    : ''
                }`}
              />
              새로고침
            </Button>
          </div>

          <div className="mt-12">
            {isError && (
              <div
                className="border-destructive/25 bg-background/95 text-destructive mb-5 rounded-xl border px-5 py-4 text-sm backdrop-blur-md"
                role="alert"
              >
                <p className="font-medium">업무 현황을 모두 불러오지 못했습니다.</p>
                <p className="mt-1">
                  {getErrorMessage(
                    assignableQuery.error ?? assignmentsQuery.error,
                    '잠시 후 새로고침해 주세요.',
                  )}
                </p>
              </div>
            )}

            <div className="grid items-stretch gap-5 lg:grid-cols-2">
              <WorkCard
                title="배정 대기"
                description="방문 가능한 신청을 확인하고 담당 업무로 수락하세요."
                count={summary.assignableCount}
                countLabel="건"
                loading={assignableQuery.isLoading}
                icon={ListChecks}
                to="/evaluations/assignable"
                action="배정 대기 목록"
              />

              <Card className="relative gap-0 overflow-hidden border-0 bg-[#f6f6f4]/95 py-0 text-foreground shadow-2xl ring-1 ring-black/10 backdrop-blur-md lg:min-h-[max(380px,calc(100svh_-_300px))]">
                <CardHeader className="relative border-b border-white/10 bg-[#202223] px-7 py-7 text-white">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex flex-wrap items-center gap-3">
                      <CardTitle className="text-3xl tracking-tight">내 담당 진행 상황</CardTitle>
                      {assignmentsQuery.isLoading ? (
                        <Skeleton className="h-9 w-16 bg-white/20" />
                      ) : (
                        <span className="text-3xl font-semibold text-white tabular tracking-tight">
                          {summary.assignmentCount}건
                        </span>
                      )}
                    </div>
                    <span className="flex size-13 shrink-0 items-center justify-center rounded-2xl border border-white/15 bg-white/10 text-white shadow-md">
                      <ClipboardCheck className="size-6" />
                    </span>
                  </div>
                </CardHeader>
                <CardContent className="relative flex flex-1 items-center bg-[#f6f6f4] px-7 py-8">
                  <div className="grid w-full grid-cols-3 gap-4">
                    <StatusCount
                      label="평가 진행 중"
                      count={summary.pendingCount}
                      loading={assignmentsQuery.isLoading}
                      icon={ClipboardCheck}
                    />
                    <StatusCount
                      label="차량 진단 완료"
                      count={summary.approvedCount}
                      loading={assignmentsQuery.isLoading}
                      icon={CheckCircle2}
                    />
                    <StatusCount
                      label="진단 반려"
                      count={summary.rejectedCount}
                      loading={assignmentsQuery.isLoading}
                      icon={XCircle}
                    />
                  </div>
                </CardContent>
                <CardFooter className="relative border-t bg-white px-7 py-6">
                  <Button
                    asChild
                    size="lg"
                    className="h-14 w-full text-base font-semibold shadow-md transition-transform duration-200 ease-out hover:scale-[1.02] active:scale-[0.99]"
                  >
                    <Link to="/evaluations/my">
                      내 담당 목록
                      <ArrowRight className="size-5" />
                    </Link>
                  </Button>
                </CardFooter>
              </Card>
            </div>
          </div>
        </div>
      </section>
    </main>
  )
}

function WorkCard({
  title,
  description,
  count,
  countLabel,
  loading,
  icon: Icon,
  to,
  action,
}: {
  title: string
  description: string
  count: number
  countLabel: string
  loading: boolean
  icon: typeof ListChecks
  to: string
  action: string
}) {
  return (
    <Card className="relative gap-0 overflow-hidden border-0 bg-[#f6f6f4]/95 py-0 text-foreground shadow-2xl ring-1 ring-black/10 backdrop-blur-md lg:min-h-[max(380px,calc(100svh_-_300px))]">
      <CardHeader className="relative border-b border-white/10 bg-[#202223] px-7 py-7 text-white">
        <div className="flex items-start justify-between gap-4">
          <CardTitle className="text-3xl tracking-tight">{title}</CardTitle>
          <span className="flex size-13 shrink-0 items-center justify-center rounded-2xl border border-white/15 bg-white/10 text-white shadow-sm">
            <Icon className="size-6" />
          </span>
        </div>
      </CardHeader>
      <CardContent className="relative flex flex-1 items-center bg-[#f6f6f4] px-7 py-8">
        <div className="border-border w-full rounded-2xl border bg-white p-7 shadow-sm">
          <p className="text-foreground/75 text-base font-semibold">현재 배정 가능한 업무</p>
          <div className="mt-4 flex items-end gap-2">
            {loading ? (
              <Skeleton className="h-16 w-24" />
            ) : (
              <strong className="tabular text-7xl leading-none font-semibold tracking-tight">
                {count}
              </strong>
            )}
            <span className="text-muted-foreground pb-1.5 text-lg font-medium">{countLabel}</span>
          </div>
          <p className="text-muted-foreground mt-5 text-sm leading-6">{description}</p>
        </div>
      </CardContent>
      <CardFooter className="relative border-t bg-white px-7 py-6">
        <Button
          asChild
          size="lg"
          className="h-14 w-full text-base font-semibold shadow-md transition-transform duration-200 ease-out hover:scale-[1.02] active:scale-[0.99]"
        >
          <Link to={to}>
            {action}
            <ArrowRight className="size-5" />
          </Link>
        </Button>
      </CardFooter>
    </Card>
  )
}

function StatusCount({
  label,
  count,
  loading,
  icon: Icon,
}: {
  label: string
  count: number
  loading: boolean
  icon: typeof CheckCircle2
}) {
  return (
    <div className="border-border flex min-h-40 flex-col justify-between rounded-2xl border bg-white p-5 shadow-sm">
      <div className="flex items-center gap-3">
        <span
          className="flex size-10 items-center justify-center rounded-xl border border-black/8 bg-[#f8f8f7] text-[#55585a] shadow-xs"
        >
          <Icon className="size-5" />
        </span>
        <p className="text-foreground/75 text-base font-semibold">{label}</p>
      </div>
      {loading ? (
        <Skeleton className="mt-6 h-10 w-14" />
      ) : (
        <p className="text-foreground tabular mt-6 text-4xl leading-none font-semibold">
          {count}
          <span className="text-muted-foreground ml-1.5 text-base font-medium">건</span>
        </p>
      )}
    </div>
  )
}
