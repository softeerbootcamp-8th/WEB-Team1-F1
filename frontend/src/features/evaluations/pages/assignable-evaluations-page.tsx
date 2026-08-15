import { useState } from 'react'
import type { InfiniteData } from '@tanstack/react-query'
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  CheckCircle2,
  Clock3,
  LoaderCircle,
  MapPin,
  Phone,
  RefreshCw,
  UserRoundCheck,
} from 'lucide-react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL, TRANSMISSION_LABEL } from '@/features/quote/types'
import { getErrorMessage } from '@/lib/axios'
import { formatDateTime } from '@/lib/format'
import { assignEvaluation, fetchAssignableEvaluations } from '../api'
import {
  ASSIGNABLE_EVALUATIONS_COUNT_QUERY_KEY,
  ASSIGNABLE_EVALUATIONS_QUERY_KEY,
  MY_ASSIGNMENTS_QUERY_KEY,
} from '../query-keys'
import type {
  AssignableEvaluationCursor,
  AssignableEvaluationsResponse,
  EvaluationAssignment,
} from '../types'
import { formatPhone, formatVisitDate, getEvaluationErrorCode } from '../utils'

type AssignablePages = InfiniteData<
  AssignableEvaluationsResponse,
  AssignableEvaluationCursor | null
>

export function AssignableEvaluationsPage() {
  const queryClient = useQueryClient()
  const [assignment, setAssignment] = useState<EvaluationAssignment | null>(null)

  // 커서 페이징이다. 커서 없이 첫 페이지를 받고, 이후에는 직전 응답의 nextCursor로 이어 읽는다.
  // nextCursor가 null이면 더 볼 것이 없다는 뜻이라 그대로 다음 페이지 없음이 된다
  const query = useInfiniteQuery({
    queryKey: ASSIGNABLE_EVALUATIONS_QUERY_KEY,
    queryFn: ({ pageParam }) => fetchAssignableEvaluations(pageParam),
    initialPageParam: null as AssignableEvaluationCursor | null,
    getNextPageParam: (lastPage) => lastPage.nextCursor,
  })

  /**
   * 목록에서 그 한 건만 지운다.
   *
   * 목록 전체를 무효화하면 지금까지 이어 읽은 페이지를 처음부터 다시 받는다 — 요청이 페이지
   * 수만큼 늘고, 그동안 목록이 비었다가 다시 차면서 보고 있던 자리를 잃는다. 사라져야 하는 것은
   * 방금 처리한 한 건뿐이다. 전체 건수는 서버가 세는 값이라 그쪽만 다시 읽는다.
   */
  const dropFromList = (evaluationId: number) => {
    queryClient.setQueryData<AssignablePages>(ASSIGNABLE_EVALUATIONS_QUERY_KEY, (pages) =>
      pages && {
        ...pages,
        pages: pages.pages.map((page) => ({
          ...page,
          evaluations: page.evaluations.filter(
            (evaluation) => evaluation.evaluationId !== evaluationId,
          ),
        })),
      },
    )
    void queryClient.invalidateQueries({ queryKey: ASSIGNABLE_EVALUATIONS_COUNT_QUERY_KEY })
  }

  const mutation = useMutation({
    mutationFn: assignEvaluation,
    onSuccess: (data, evaluationId) => {
      setAssignment(data)
      dropFromList(evaluationId)
      void queryClient.invalidateQueries({ queryKey: MY_ASSIGNMENTS_QUERY_KEY })
    },
    onError: (error, evaluationId) => {
      const code = getEvaluationErrorCode(error)
      if (code === 'EVALUATION_ALREADY_ASSIGNED') {
        toast.info('이미 마감됐습니다', {
          description: '다른 평가사가 먼저 수락해 목록에서 내렸습니다.',
        })
        dropFromList(evaluationId)
        return
      }
      if (code === 'EVALUATION_NOT_ASSIGNABLE') {
        toast.info('수락할 수 없는 신청입니다', {
          description: '신청 상태가 변경되어 목록에서 내렸습니다.',
        })
        dropFromList(evaluationId)
        return
      }
      toast.error(getErrorMessage(error, '방문견적을 수락하지 못했습니다'))
    },
  })

  const evaluations = query.data?.pages.flatMap((page) => page.evaluations) ?? []

  // "더 보기"도 조회라 isFetching만 보면 다음 페이지를 읽는 동안 새로고침 버튼이 함께 돈다.
  // 두 버튼이 각자 무엇을 하는지 보여야 하므로 이어 읽기는 빼고 본다
  const isRefreshing = query.isFetching && !query.isFetchingNextPage

  return (
    <main className="mx-auto max-w-7xl px-6 py-12" aria-label="배정 대기 방문견적">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div>
          <h1 className="text-3xl font-semibold md:text-4xl">배정 대기 목록</h1>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void query.refetch()} disabled={isRefreshing}>
            <RefreshCw className={isRefreshing ? 'animate-spin' : ''} />
            새로고침
          </Button>
          <Button asChild variant="secondary">
            <Link to="/evaluations/my">내 담당 목록</Link>
          </Button>
        </div>
      </header>

      {query.isLoading ? (
        <div className="flex min-h-64 items-center justify-center">
          <LoaderCircle className="size-7 animate-spin" aria-label="목록 불러오는 중" />
        </div>
      ) : query.isError ? (
        <EmptyState
          title="배정 대기 목록을 불러오지 못했습니다"
          description={getErrorMessage(query.error, '잠시 후 다시 시도해 주세요.')}
          action={<Button onClick={() => void query.refetch()}>다시 시도</Button>}
        />
      ) : evaluations.length === 0 ? (
        <EmptyState
          icon={UserRoundCheck}
          title="현재 배정 대기 중인 신청이 없습니다"
          description="새 신청이 접수되면 이곳에 표시됩니다."
        />
      ) : (
        <ul className="space-y-3">
          {evaluations.map((evaluation) => (
            <li key={evaluation.evaluationId}>
              <Card className="gap-0 overflow-hidden border-border/80 py-0 shadow-sm transition-[border-color,box-shadow] hover:border-foreground/15 hover:shadow-md">
                <CardContent className="grid p-0 lg:grid-cols-[minmax(0,1fr)_13rem]">
                  <div className="min-w-0 p-5 sm:p-6">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <h2 className="text-xl font-semibold">
                        {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
                      </h2>
                    </div>

                    <p className="text-muted-foreground mt-2 text-sm">
                      {evaluation.modelYear}년식 · {evaluation.plateNumber} ·{' '}
                      {FUEL_TYPE_LABEL[evaluation.fuelType]} ·{' '}
                      {TRANSMISSION_LABEL[evaluation.transmission]}
                    </p>

                    <div className="mt-5 grid divide-y border-t pt-4 sm:grid-cols-3 sm:divide-x sm:divide-y-0">
                      <AssignmentMeta
                        icon={CalendarDays}
                        label="방문 희망일"
                        value={formatVisitDate(evaluation.visitDate)}
                        emphasis
                      />
                      <AssignmentMeta
                        icon={MapPin}
                        label="방문 위치"
                        value={evaluation.visitAddress}
                      />
                      <AssignmentMeta
                        icon={Clock3}
                        label="신청 날짜"
                        value={formatDateTime(evaluation.requestedAt)}
                      />
                    </div>
                  </div>

                  <div className="flex items-center border-t p-5 lg:border-t-0 lg:border-l">
                    <Button
                      className="h-11 w-full"
                      disabled={mutation.isPending}
                      onClick={() => mutation.mutate(evaluation.evaluationId)}
                    >
                      {mutation.isPending && mutation.variables === evaluation.evaluationId ? (
                        <LoaderCircle className="animate-spin" />
                      ) : (
                        <UserRoundCheck />
                      )}
                      방문견적 수락
                    </Button>
                  </div>
                </CardContent>
              </Card>
            </li>
          ))}
        </ul>
      )}

      {/* 더 볼 것이 남았는지를 버튼의 유무로 알린다. 다 읽었으면 끝이라고 말해 준다 —
          목록이 짧아서 끝난 것인지 아직 덜 받은 것인지 화면만 보고는 구분할 수 없다 */}
      {evaluations.length > 0 && (
        <div className="mt-8 flex justify-center">
          {query.hasNextPage ? (
            <Button
              variant="outline"
              onClick={() => void query.fetchNextPage()}
              disabled={query.isFetchingNextPage}
            >
              {query.isFetchingNextPage && <LoaderCircle className="animate-spin" />}
              더 보기
            </Button>
          ) : (
            <p className="text-muted-foreground text-sm">
              배정 대기 중인 신청을 모두 확인했습니다.
            </p>
          )}
        </div>
      )}

      <Dialog open={Boolean(assignment)} onOpenChange={(open) => !open && setAssignment(null)}>
        <DialogContent>
          <DialogHeader>
            <div className="bg-success/10 text-success mb-2 flex size-11 items-center justify-center rounded-full">
              <CheckCircle2 className="size-5" />
            </div>
            <DialogTitle>방문견적을 수락했습니다</DialogTitle>
            <DialogDescription>
              판매자에게 연락해 방문 일정을 협의해 주세요. 연락처는 내 담당 상세에서도 다시 확인할 수 있습니다.
            </DialogDescription>
          </DialogHeader>
          {assignment && (
            <dl className="bg-muted/50 space-y-4 rounded-xl p-5 text-sm">
              <div>
                <dt className="text-muted-foreground">차량 · 방문일</dt>
                <dd className="mt-1 font-medium">
                  {assignment.plateNumber} · {formatVisitDate(assignment.visitDate)}
                </dd>
              </div>
              <div>
                <dt className="text-muted-foreground">방문 주소</dt>
                <dd className="mt-1 break-words font-medium">{assignment.visitAddress}</dd>
              </div>
              <div>
                <dt className="text-muted-foreground">판매자 연락처</dt>
                <dd className="mt-1">
                  <a className="inline-flex items-center gap-2 text-lg font-semibold underline-offset-4 hover:underline" href={`tel:${assignment.contactPhone}`}>
                    <Phone className="size-4" />
                    {formatPhone(assignment.contactPhone)}
                  </a>
                </dd>
              </div>
            </dl>
          )}
          <DialogFooter>
            <DialogClose asChild>
              <Button variant="outline">닫기</Button>
            </DialogClose>
            <Button asChild>
              <Link to="/evaluations/my">내 담당 목록으로</Link>
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </main>
  )
}

function AssignmentMeta({
  icon: Icon,
  label,
  value,
  emphasis = false,
}: {
  icon: typeof CalendarDays
  label: string
  value: string
  emphasis?: boolean
}) {
  return (
    <div className="min-w-0 py-3 first:pt-0 last:pb-0 sm:px-4 sm:py-0 sm:first:pl-0 sm:last:pr-0">
      <p className="text-muted-foreground flex items-center gap-1.5 text-xs font-medium">
        <Icon className="size-3.5" />
        {label}
      </p>
      <p className={`mt-2 line-clamp-2 break-words text-sm leading-5 ${emphasis ? 'font-semibold' : 'font-medium'}`} title={value}>
        {value}
      </p>
    </div>
  )
}
