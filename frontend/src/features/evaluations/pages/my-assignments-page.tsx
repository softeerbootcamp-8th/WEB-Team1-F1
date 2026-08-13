import { useQuery } from '@tanstack/react-query'
import { ClipboardCheck, LoaderCircle, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { fetchMyAssignments } from '../api'
import { EvaluationSummaryCard } from '../components/evaluation-summary-card'

export function MyAssignmentsPage() {
  const query = useQuery({
    queryKey: ['evaluations', 'my-assignments'],
    queryFn: fetchMyAssignments,
  })
  const evaluations = query.data?.evaluations ?? []

  return (
    <main className="mx-auto max-w-7xl px-6 py-12" aria-label="내 담당 방문견적">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div>
          <h1 className="text-3xl font-semibold md:text-4xl">내 담당 목록</h1>
          <p className="text-muted-foreground mt-3">
            판매자와 일정을 협의한 뒤 현장에서 진단 결과를 등록해 주세요.
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => void query.refetch()} disabled={query.isFetching}>
            <RefreshCw className={query.isFetching ? 'animate-spin' : ''} />
            새로고침
          </Button>
          <Button asChild>
            <Link to="/evaluations/assignable">배정 대기 보기</Link>
          </Button>
        </div>
      </header>

      {query.isLoading ? (
        <div className="flex min-h-64 items-center justify-center">
          <LoaderCircle className="size-7 animate-spin" aria-label="목록 불러오는 중" />
        </div>
      ) : query.isError ? (
        <EmptyState
          title="내 담당 목록을 불러오지 못했습니다"
          description={getErrorMessage(query.error, '잠시 후 다시 시도해 주세요.')}
          action={<Button onClick={() => void query.refetch()}>다시 시도</Button>}
        />
      ) : evaluations.length === 0 ? (
        <EmptyState
          icon={ClipboardCheck}
          title="아직 담당 중인 방문견적이 없습니다"
          description="배정 대기 목록에서 방문 가능한 신청을 수락해 주세요."
          action={
            <Button asChild>
              <Link to="/evaluations/assignable">배정 대기 보기</Link>
            </Button>
          }
        />
      ) : (
        <ul className="space-y-3">
          {evaluations.map((evaluation) => (
            <li key={evaluation.evaluationId}>
              <EvaluationSummaryCard
                evaluation={evaluation}
                layout="list"
                viewer="evaluator"
                action={
                  evaluation.status === 'REJECTED' ? (
                    <Button className="w-full" disabled>
                      반려 처리됨
                    </Button>
                  ) : evaluation.auctionStatus ? (
                    <Button className="w-full" disabled>
                      경매 등록됨 · 수정 불가
                    </Button>
                  ) : (
                    <Button
                      asChild
                      variant={evaluation.status === 'APPROVED' ? 'outline' : 'default'}
                      className={
                        evaluation.status === 'APPROVED'
                          ? 'border-primary bg-primary/5 text-primary hover:bg-primary/10 hover:text-primary w-full'
                          : 'w-full'
                      }
                    >
                      <Link to={`/evaluations/${evaluation.evaluationId}/result`}>
                        {evaluation.status === 'APPROVED'
                          ? '진단 확인·수정'
                          : '진단 작성하기'}
                      </Link>
                    </Button>
                  )
                }
              />
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}
