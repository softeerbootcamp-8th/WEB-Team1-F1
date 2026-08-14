import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ClipboardList, LoaderCircle } from 'lucide-react'
import { Link } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { fetchMyRequests } from '../api'
import { EvaluationSummaryCard } from './evaluation-summary-card'

export function MyRequestsPanel() {
  const query = useQuery({
    queryKey: ['evaluations', 'my-requests'],
    queryFn: fetchMyRequests,
  })
  const evaluations = query.data?.evaluations ?? []

  if (query.isLoading) {
    return (
      <div className="flex min-h-56 items-center justify-center">
        <LoaderCircle className="size-6 animate-spin" aria-label="신청 목록 불러오는 중" />
      </div>
    )
  }

  if (query.isError) {
    return (
      <EmptyState
        title="방문견적 신청을 불러오지 못했습니다"
        description={getErrorMessage(query.error, '잠시 후 다시 시도해 주세요.')}
        action={<Button onClick={() => void query.refetch()}>다시 시도</Button>}
      />
    )
  }

  if (evaluations.length === 0) {
    return (
      <EmptyState
        icon={ClipboardList}
        title="방문견적 신청 내역이 없습니다"
        description="차량 정보를 확인하고 방문견적을 신청해 보세요."
        action={<Button asChild><Link to="/sell">방문견적 신청하기</Link></Button>}
      />
    )
  }

  return (
    <ul className="space-y-3">
      {evaluations.map((evaluation) => (
        <li key={evaluation.evaluationId}>
          <EvaluationSummaryCard
            evaluation={evaluation}
            layout="list"
            action={
              <Button
                asChild
                className="h-11 w-full"
              >
                <Link to={`/mypage/evaluations/${evaluation.evaluationId}`}>
                  {evaluation.status === 'APPROVED' ? '진단 결과 확인' : '신청 상세 보기'}
                  <ArrowRight />
                </Link>
              </Button>
            }
          />
        </li>
      ))}
    </ul>
  )
}
