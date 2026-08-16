import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ClipboardCheck, LoaderCircle, RefreshCw } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { fetchMyAssignments } from '../api'
import { EvaluationScopeTabs, type ScopeOption } from '../components/evaluation-scope-tabs'
import { EvaluationSummaryCard } from '../components/evaluation-summary-card'
import { myAssignmentsQueryKey } from '../query-keys'
import type { EvaluationAssignmentScope } from '../types'

/**
 * 어느 탭을 보고 있었는지는 주소에 남긴다. 화면 상태로만 들고 있으면 완료 목록에서 한 건을
 * 열어 보고 돌아왔을 때 목록이 새로 마운트되면서 진행 중으로 풀린다(경매 목록이 같은 이유로
 * status·scope를 주소에 둔다). 기본값인 진행 중은 주소에 적지 않아 /evaluations/my를 깨끗하게 둔다.
 */
const SCOPE_PARAM = 'scope'

// "전체" 탭은 두지 않는다. 두 탭의 합이 곧 전체라 세 번째 선택지는 같은 것을 두 번 보여줄
// 뿐이고, 상태별 건수가 필요한 평가사 홈은 목록이 아니라 건수 조회를 쓴다.
const SCOPES: ScopeOption<EvaluationAssignmentScope>[] = [
  { value: 'ACTIVE', label: '진행 중' },
  { value: 'COMPLETED', label: '완료' },
]

function readScope(params: URLSearchParams): EvaluationAssignmentScope {
  return params.get(SCOPE_PARAM)?.toUpperCase() === 'COMPLETED' ? 'COMPLETED' : 'ACTIVE'
}

export function MyAssignmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const scope = readScope(searchParams)

  const query = useQuery({
    // 범위가 키에 들어가야 탭을 옮긴 첫 순간에 지난 목록이 그대로 보이지 않는다
    queryKey: myAssignmentsQueryKey(scope),
    queryFn: () => fetchMyAssignments(scope),
  })
  const evaluations = query.data?.evaluations ?? []

  // 탭 전환은 히스토리에 쌓지 않는다. 쌓으면 탭을 옮긴 횟수만큼 뒤로가기를 눌러야 목록을 벗어난다
  const selectScope = (next: EvaluationAssignmentScope) => {
    const params = new URLSearchParams(searchParams)
    if (next === 'ACTIVE') params.delete(SCOPE_PARAM)
    else params.set(SCOPE_PARAM, next)
    setSearchParams(params, { replace: true })
  }

  return (
    <main className="mx-auto max-w-7xl px-6 py-12" aria-label="내 담당 방문견적">
      <header className="mb-8 flex flex-wrap items-end justify-between gap-5">
        <div>
          <h1 className="text-3xl font-semibold md:text-4xl">내 담당 목록</h1>
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

      <div className="mb-6">
        <EvaluationScopeTabs
          value={scope}
          options={SCOPES}
          onChange={selectScope}
          label="담당 목록 범위 선택"
        />
      </div>

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
        // 빈 목록에서 할 일은 범위마다 다르다. 완료 탭에서 "배정 대기 보기"만 주면
        // 방금 고른 탭이 잘못된 것인지 담당이 없는 것인지 알 수 없다
        scope === 'COMPLETED' ? (
          <EmptyState
            icon={ClipboardCheck}
            title="완료한 진단이 없습니다"
            description="진단을 제출하거나 반려하면 여기로 옮겨집니다."
            action={<Button onClick={() => selectScope('ACTIVE')}>진행 중 목록 보기</Button>}
          />
        ) : (
          <EmptyState
            icon={ClipboardCheck}
            title="진행 중인 진단이 없습니다"
            description="배정 대기 목록에서 방문 가능한 신청을 수락해 주세요. 끝낸 진단은 완료 탭에 있습니다."
            action={
              <Button asChild>
                <Link to="/evaluations/assignable">배정 대기 보기</Link>
              </Button>
            }
          />
        )
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
                    <Button className="h-11 w-full" disabled>
                      반려 처리됨
                    </Button>
                  ) : evaluation.auctionStatus ? (
                    <Button className="h-11 w-full" disabled>
                      경매 등록됨 · 수정 불가
                    </Button>
                  ) : (
                    <Button
                      asChild
                      className="h-11 w-full"
                    >
                      {/* 보고 있던 탭을 함께 넘긴다. 결과 화면의 "내 담당 목록"이
                          돌아올 자리를 이 값으로 정한다 */}
                      <Link
                        to={`/evaluations/${evaluation.evaluationId}/result${
                          scope === 'COMPLETED' ? '?scope=COMPLETED' : ''
                        }`}
                      >
                        {evaluation.status === 'APPROVED'
                          ? '진단 확인·수정'
                          : '진단 작성하기'}
                        <ArrowRight />
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
