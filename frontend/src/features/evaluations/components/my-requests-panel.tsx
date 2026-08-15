import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ClipboardList, LoaderCircle } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { fetchMyRequests } from '../api'
import {
  countRequests,
  needsListing,
  selectRequests,
  type EvaluationRequestScope,
} from '../request-scope'
import { EvaluationScopeTabs, type ScopeOption } from './evaluation-scope-tabs'
import { EvaluationSummaryCard } from './evaluation-summary-card'

/**
 * 보던 탭은 주소에 남긴다. 화면 상태로만 들고 있으면 신청 상세를 열어 보고 돌아왔을 때
 * 패널이 새로 마운트되면서 진행 중으로 풀린다(담당 목록이 같은 규칙을 쓴다).
 */
const SCOPE_PARAM = 'scope'

function readScope(params: URLSearchParams): EvaluationRequestScope {
  return params.get(SCOPE_PARAM)?.toUpperCase() === 'CLOSED' ? 'CLOSED' : 'ACTIVE'
}

/**
 * 판매자의 진단 신청 내역.
 *
 * **목록을 서버에서 나누지 않는다.** 끝났는지를 가르는 조건에 최신 경매 상태가 들어가는데,
 * 그건 지금 목록을 읽은 뒤 차량별로 붙이는 값이라 조회 조건으로 쓰려면 "차량별 최신 경매"를
 * 서브쿼리로 끌고 들어와야 한다. 게다가 신청 상세와 경매 등록 화면의 출품 가드가 이 목록
 * 전체에서 자기 차량의 경매 상태를 찾아 쓰고 있어(`useVehicleAuctionStatus`), 서버가 잘라
 * 보내는 순간 범위 밖의 건은 상태를 못 찾아 가드가 조용히 열린다.
 *
 * 판매자 한 명의 신청 수는 본인이 낸 만큼이라 전량을 받아도 부담이 없다. 나누어 읽어야 할
 * 만큼 쌓이는 날에는 상세 응답에 경매 상태를 실어 그 의존부터 끊어야 한다.
 */
export function MyRequestsPanel() {
  const [searchParams, setSearchParams] = useSearchParams()
  const scope = readScope(searchParams)

  const query = useQuery({
    queryKey: ['evaluations', 'my-requests'],
    queryFn: fetchMyRequests,
  })
  const evaluations = query.data?.evaluations ?? []
  const visible = selectRequests(evaluations, scope)

  // 탭 전환은 히스토리에 쌓지 않는다. 쌓으면 탭을 옮긴 횟수만큼 뒤로가기를 눌러야 벗어난다
  const selectScope = (next: EvaluationRequestScope) => {
    const params = new URLSearchParams(searchParams)
    if (next === 'ACTIVE') params.delete(SCOPE_PARAM)
    else params.set(SCOPE_PARAM, next)
    setSearchParams(params, { replace: true })
  }

  const scopes: ScopeOption<EvaluationRequestScope>[] = [
    { value: 'ACTIVE', label: '진행 중', count: countRequests(evaluations, 'ACTIVE') },
    { value: 'CLOSED', label: '종료', count: countRequests(evaluations, 'CLOSED') },
  ]

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

  // 신청을 한 번도 내지 않은 사람에게는 탭을 보여줄 이유가 없다. 양쪽이 다 비어 있는 탭 줄은
  // 무엇을 고르라는 것인지 알려 주지 못한다
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
    <div>
      <EvaluationScopeTabs
        value={scope}
        options={scopes}
        onChange={selectScope}
        label="신청 내역 범위 선택"
      />

      {visible.length === 0 ? (
        <div className="mt-6">
          {scope === 'CLOSED' ? (
            <EmptyState
              icon={ClipboardList}
              title="종료된 신청이 없습니다"
              description="반려되었거나 낙찰이 끝난 신청이 여기로 옮겨집니다."
              action={<Button onClick={() => selectScope('ACTIVE')}>진행 중 보기</Button>}
            />
          ) : (
            <EmptyState
              icon={ClipboardList}
              title="진행 중인 신청이 없습니다"
              description="끝난 신청은 종료 탭에 있습니다. 새 차량은 방문견적부터 신청해 주세요."
              action={<Button asChild><Link to="/sell">방문견적 신청하기</Link></Button>}
            />
          )}
        </div>
      ) : (
        <ul className="mt-6 space-y-3">
          {visible.map((evaluation) => (
            <li key={evaluation.evaluationId}>
              <EvaluationSummaryCard
                evaluation={evaluation}
                layout="list"
                // 위로 끌어올린 이유를 카드가 직접 말한다
                badge={
                  needsListing(evaluation) ? (
                    <Badge
                      variant="outline"
                      className="border-success/25 bg-success/10 text-success rounded-full px-3 py-1 text-sm font-semibold"
                    >
                      출품 대기
                    </Badge>
                  ) : undefined
                }
                action={
                  <Button
                    asChild
                    className="h-11 w-full"
                  >
                    {/* 보고 있던 탭을 함께 넘긴다. 상세의 "마이페이지"가 돌아올 자리를 이 값으로 정한다 */}
                    <Link
                      to={`/mypage/evaluations/${evaluation.evaluationId}${
                        scope === 'CLOSED' ? '?scope=CLOSED' : ''
                      }`}
                    >
                      {needsListing(evaluation)
                        ? '진단 결과 확인 · 출품'
                        : evaluation.status === 'APPROVED'
                          ? '진단 결과 확인'
                          : '신청 상세 보기'}
                      <ArrowRight />
                    </Link>
                  </Button>
                }
              />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
