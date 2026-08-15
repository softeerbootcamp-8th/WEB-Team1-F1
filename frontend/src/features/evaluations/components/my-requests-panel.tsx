import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ClipboardList, LoaderCircle } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { fetchMyRequests } from '../api'
import {
  ACTIVE_SCOPE,
  countRequests,
  isRequestScope,
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

/** 라벨은 판매자가 읽을 말로 적는다. "READY_TO_LIST"가 아니라 "출품 대기"가 화면의 언어다 */
const SCOPE_LABEL: Record<EvaluationRequestScope, string> = {
  ACTIVE: '진행 중',
  PENDING_ASSIGNMENT: '배정 대기',
  EVALUATING: '평가 중',
  READY_TO_LIST: '출품 대기',
  IN_AUCTION: '경매 중',
  CLOSED: '종료',
}

const SCOPE_ORDER: EvaluationRequestScope[] = [
  ACTIVE_SCOPE,
  'PENDING_ASSIGNMENT',
  'EVALUATING',
  'READY_TO_LIST',
  'IN_AUCTION',
  'CLOSED',
]

/**
 * 빈 칸에서 할 말. 범위마다 다르다 — "신청 내역이 없습니다" 하나로 두면 배정을 기다리는 건이
 * 없을 뿐인 사람에게 신청을 한 적도 없다고 말하게 된다.
 */
const EMPTY_MESSAGE: Record<EvaluationRequestScope, { title: string; description: string }> = {
  ACTIVE: {
    title: '진행 중인 신청이 없습니다',
    description: '끝난 신청은 종료 탭에 있습니다. 새 차량은 방문견적부터 신청해 주세요.',
  },
  PENDING_ASSIGNMENT: {
    title: '배정을 기다리는 신청이 없습니다',
    description: '신청을 내면 평가사가 수락할 때까지 이 칸에 머무릅니다.',
  },
  EVALUATING: {
    title: '평가가 진행 중인 신청이 없습니다',
    description: '평가사가 수락하면 여기로 옮겨집니다.',
  },
  READY_TO_LIST: {
    title: '출품할 수 있는 차량이 없습니다',
    description: '진단이 끝나면 이 칸에서 경매로 출품할 수 있습니다. 유찰된 차량도 여기로 돌아옵니다.',
  },
  IN_AUCTION: {
    title: '경매가 걸린 차량이 없습니다',
    description: '출품하면 경매가 끝날 때까지 이 칸에 머무릅니다.',
  },
  CLOSED: {
    title: '종료된 신청이 없습니다',
    description: '반려되었거나 낙찰이 끝난 신청이 여기로 옮겨집니다.',
  },
}

function readScope(params: URLSearchParams): EvaluationRequestScope {
  const raw = params.get(SCOPE_PARAM)?.toUpperCase() ?? ''

  return isRequestScope(raw) ? raw : ACTIVE_SCOPE
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
 * 전량을 받는 덕에 탭마다 건수를 함께 보여줄 수 있다. 판매자 한 명의 신청 수는 본인이 낸
 * 만큼이라 부담도 없다. 나누어 읽어야 할 만큼 쌓이는 날에는 상세 응답에 경매 상태를 실어 그
 * 의존부터 끊어야 한다.
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
    if (next === ACTIVE_SCOPE) params.delete(SCOPE_PARAM)
    else params.set(SCOPE_PARAM, next)
    setSearchParams(params, { replace: true })
  }

  const scopes: ScopeOption<EvaluationRequestScope>[] = SCOPE_ORDER.map((value) => ({
    value,
    label: SCOPE_LABEL[value],
    count: countRequests(evaluations, value),
  }))

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

  // 신청을 한 번도 내지 않은 사람에게는 탭을 보여줄 이유가 없다. 여섯 칸이 모두 0인 탭 줄은
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
          <EmptyState
            icon={ClipboardList}
            {...EMPTY_MESSAGE[scope]}
            action={
              scope === ACTIVE_SCOPE ? (
                <Button asChild><Link to="/sell">방문견적 신청하기</Link></Button>
              ) : (
                // 좁혀 본 칸이 비었을 때 할 일은 신청이 아니라 되돌아가기다
                <Button onClick={() => selectScope(ACTIVE_SCOPE)}>진행 중 보기</Button>
              )
            }
          />
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
                        scope === ACTIVE_SCOPE ? '' : `?scope=${scope}`
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
