import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  CalendarDays,
  CheckCircle2,
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
import type { EvaluationAssignment } from '../types'
import { formatPhone, formatVisitDate, getEvaluationErrorCode } from '../utils'

const ASSIGNABLE_QUERY_KEY = ['evaluations', 'assignable'] as const

export function AssignableEvaluationsPage() {
  const queryClient = useQueryClient()
  const [assignment, setAssignment] = useState<EvaluationAssignment | null>(null)

  const query = useQuery({
    queryKey: ASSIGNABLE_QUERY_KEY,
    queryFn: fetchAssignableEvaluations,
  })

  const mutation = useMutation({
    mutationFn: assignEvaluation,
    onSuccess: (data) => {
      setAssignment(data)
      void queryClient.invalidateQueries({ queryKey: ASSIGNABLE_QUERY_KEY })
      void queryClient.invalidateQueries({ queryKey: ['evaluations', 'my-assignments'] })
    },
    onError: (error) => {
      const code = getEvaluationErrorCode(error)
      if (code === 'EVALUATION_ALREADY_ASSIGNED') {
        toast.info('이미 마감됐습니다', {
          description: '다른 평가사가 먼저 수락해 목록을 새로고침했습니다.',
        })
        void queryClient.invalidateQueries({ queryKey: ASSIGNABLE_QUERY_KEY })
        return
      }
      if (code === 'EVALUATION_NOT_ASSIGNABLE') {
        toast.info('수락할 수 없는 신청입니다', {
          description: '신청 상태가 변경되어 목록을 새로고침했습니다.',
        })
        void queryClient.invalidateQueries({ queryKey: ASSIGNABLE_QUERY_KEY })
        return
      }
      toast.error(getErrorMessage(error, '방문견적을 수락하지 못했습니다'))
    },
  })

  const evaluations = query.data?.evaluations ?? []

  return (
    <main className="mx-auto max-w-7xl px-6 py-12" aria-label="배정 대기 방문견적">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div>
          <p className="text-muted-foreground text-sm tracking-[0.15em] uppercase">
            Evaluator Queue
          </p>
          <h1 className="mt-2 text-3xl font-semibold md:text-4xl">배정 대기 목록</h1>
          <p className="text-muted-foreground mt-3">
            방문일이 가까운 신청부터 표시됩니다. 수락한 평가사에게만 연락처가 공개됩니다.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}>
            <RefreshCw className={query.isFetching ? 'animate-spin' : ''} />
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
              <Card className="gap-0 py-0">
                <CardContent className="flex flex-col gap-5 p-5 sm:p-6 lg:flex-row lg:items-center">
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-3">
                      <h2 className="text-lg font-semibold">
                        {MANUFACTURER_LABEL[evaluation.manufacturer]} {evaluation.model}
                      </h2>
                    </div>

                    <p className="text-muted-foreground mt-2 text-sm">
                      {evaluation.modelYear}년식 · {evaluation.plateNumber} ·{' '}
                      {FUEL_TYPE_LABEL[evaluation.fuelType]} ·{' '}
                      {TRANSMISSION_LABEL[evaluation.transmission]}
                    </p>

                    <div className="mt-4 grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-[1fr_1.4fr_auto]">
                      <div className="flex min-w-0 gap-2.5">
                        <CalendarDays className="text-muted-foreground mt-0.5 size-4 shrink-0" />
                        <strong>{formatVisitDate(evaluation.visitDate)}</strong>
                      </div>
                      <div className="flex min-w-0 gap-2.5">
                        <MapPin className="text-muted-foreground mt-0.5 size-4 shrink-0" />
                        <span className="break-words">{evaluation.visitAddress}</span>
                      </div>
                      <p className="text-muted-foreground text-xs md:col-span-2 xl:col-span-1 xl:self-center xl:text-right">
                        {formatDateTime(evaluation.requestedAt)} 접수
                      </p>
                    </div>
                  </div>

                  <div className="shrink-0 lg:border-l lg:pl-6">
                    <Button
                      className="w-full lg:w-40"
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
                <dd className="mt-1 font-medium">{assignment.visitAddress}</dd>
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
