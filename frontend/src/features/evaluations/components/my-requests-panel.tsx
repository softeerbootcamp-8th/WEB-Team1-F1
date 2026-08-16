import { useQuery } from '@tanstack/react-query'
import { ArrowRight, ClipboardList, LoaderCircle } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/components/common/empty-state'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { getErrorMessage } from '@/lib/axios'
import { cn } from '@/lib/utils'
import { fetchMyRequests } from '../api'
import {
  DEFAULT_BUCKET,
  countRequests,
  isBucket,
  isStateOf,
  needsListing,
  selectRequests,
  STATES_BY_BUCKET,
  type EvaluationRequestBucket,
  type EvaluationRequestState,
} from '../request-scope'
import { EvaluationScopeTabs, type ScopeOption } from './evaluation-scope-tabs'
import { EvaluationSummaryCard } from './evaluation-summary-card'

/**
 * 보던 자리는 주소에 남긴다. 화면 상태로만 들고 있으면 신청 상세를 열어 보고 돌아왔을 때
 * 패널이 새로 마운트되면서 처음으로 풀린다(담당 목록이 같은 규칙을 쓴다).
 *
 * 두 값을 따로 싣는다. 큰 틀과 상태가 다른 층이라 한 값에 겹쳐 담으면, 상태를 껐을 때 어느
 * 틀로 돌아갈지를 주소가 말해 주지 못한다(경매 목록도 scope와 status를 따로 쓴다).
 */
const BUCKET_PARAM = 'scope'
const STATE_PARAM = 'state'

const BUCKET_LABEL: Record<EvaluationRequestBucket, string> = {
  ACTIVE: '진행 중',
  CLOSED: '종료',
}

/** 라벨은 판매자가 읽을 말로 적는다. "READY_TO_LIST"가 아니라 "출품 대기"가 화면의 언어다 */
const STATE_LABEL: Record<EvaluationRequestState, string> = {
  PENDING_ASSIGNMENT: '배정 대기',
  EVALUATING: '평가 중',
  READY_TO_LIST: '출품 대기',
  AUCTION_SCHEDULED: '경매 예정',
  IN_AUCTION: '경매 중',
  REJECTED: '반려',
  SOLD: '낙찰 완료',
}

/**
 * 빈 칸에서 할 말. 좁힌 만큼 다르게 말한다 — "신청 내역이 없습니다" 하나로 두면 배정을
 * 기다리는 건이 없을 뿐인 사람에게 신청을 한 적도 없다고 말하게 된다.
 */
const STATE_EMPTY: Record<EvaluationRequestState, string> = {
  PENDING_ASSIGNMENT: '배정을 기다리는 신청이 없습니다',
  EVALUATING: '평가가 진행 중인 신청이 없습니다',
  READY_TO_LIST: '출품할 수 있는 차량이 없습니다',
  AUCTION_SCHEDULED: '시작을 기다리는 경매가 없습니다',
  IN_AUCTION: '진행 중인 경매가 없습니다',
  REJECTED: '반려된 신청이 없습니다',
  SOLD: '낙찰된 차량이 없습니다',
}

const BUCKET_EMPTY: Record<EvaluationRequestBucket, { title: string; description: string }> = {
  ACTIVE: {
    title: '진행 중인 신청이 없습니다',
    description: '끝난 신청은 종료 탭에 있습니다. 새 차량은 방문견적부터 신청해 주세요.',
  },
  CLOSED: {
    title: '종료된 신청이 없습니다',
    description: '반려되었거나 낙찰이 끝난 신청이 여기로 옮겨집니다.',
  },
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
 * 전량을 받는 덕에 탭과 칩마다 건수를 함께 보여줄 수 있다. 판매자 한 명의 신청 수는 본인이 낸
 * 만큼이라 부담도 없다. 나누어 읽어야 할 만큼 쌓이는 날에는 상세 응답에 경매 상태를 실어 그
 * 의존부터 끊어야 한다.
 */
export function MyRequestsPanel() {
  const [searchParams, setSearchParams] = useSearchParams()

  const requestedBucket = searchParams.get(BUCKET_PARAM)?.toUpperCase() ?? ''
  const bucket: EvaluationRequestBucket = isBucket(requestedBucket)
    ? requestedBucket
    : DEFAULT_BUCKET

  const requestedState = searchParams.get(STATE_PARAM)?.toUpperCase() ?? ''
  // 큰 틀에 없는 상태는 버린다. 어긋난 짝을 그대로 두면 어느 칩도 켜지지 않은 채 빈 목록만 나온다
  const state = isStateOf(requestedState, bucket)
    ? (requestedState as EvaluationRequestState)
    : null

  const query = useQuery({
    queryKey: ['evaluations', 'my-requests'],
    queryFn: fetchMyRequests,
  })
  const evaluations = query.data?.evaluations ?? []
  const visible = selectRequests(evaluations, bucket, state)

  // 자리를 옮기는 것은 히스토리에 쌓지 않는다. 쌓으면 옮긴 횟수만큼 뒤로가기를 눌러야 벗어난다
  const move = (nextBucket: EvaluationRequestBucket, nextState: EvaluationRequestState | null) => {
    const params = new URLSearchParams(searchParams)
    if (nextBucket === DEFAULT_BUCKET) params.delete(BUCKET_PARAM)
    else params.set(BUCKET_PARAM, nextBucket)

    // 큰 틀을 옮기면 상태는 따라오지 못한다. 남겨 두면 방금 고른 틀과 어긋난 주소가 된다
    if (nextState) params.set(STATE_PARAM, nextState)
    else params.delete(STATE_PARAM)

    setSearchParams(params, { replace: true })
  }

  const buckets: ScopeOption<EvaluationRequestBucket>[] = (['ACTIVE', 'CLOSED'] as const).map(
    (value) => ({ value, label: BUCKET_LABEL[value], count: countRequests(evaluations, value) }),
  )

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

  // 신청을 한 번도 내지 않은 사람에게는 탭도 칩도 보여줄 이유가 없다. 전부 0인 조작 줄은
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
        value={bucket}
        options={buckets}
        onChange={(next) => move(next, null)}
        label="신청 내역 범위 선택"
      />

      {/* 상태는 큰 틀 아래 층이라 탭이 아니라 칩이다. 같은 줄에 같은 크기로 세우면 "진행 중"과
          "출품 대기"가 형제로 읽혀 무엇이 무엇을 품는지 사라진다 */}
      <div
        className="mt-4 flex flex-wrap justify-end gap-2"
        role="group"
        aria-label={`${BUCKET_LABEL[bucket]} 상태 필터`}
      >
        {STATES_BY_BUCKET[bucket].map((value) => (
          <StateChip
            key={value}
            selected={state === value}
            count={countRequests(evaluations, bucket, value)}
            // 켜진 것을 다시 누르면 전체로 돌아간다. "전체" 칩을 따로 두지 않는 이유다
            onClick={() => move(bucket, state === value ? null : value)}
          >
            {STATE_LABEL[value]}
          </StateChip>
        ))}
      </div>

      {visible.length === 0 ? (
        <div className="mt-6">
          <EmptyState
            icon={ClipboardList}
            title={state ? STATE_EMPTY[state] : BUCKET_EMPTY[bucket].title}
            description={
              state
                ? '다른 상태를 고르거나 필터를 꺼서 전체를 볼 수 있습니다.'
                : BUCKET_EMPTY[bucket].description
            }
            action={
              state ? (
                // 좁혀 본 칸이 비었을 때 할 일은 신청이 아니라 필터를 끄는 것이다
                <Button onClick={() => move(bucket, null)}>
                  {BUCKET_LABEL[bucket]} 전체 보기
                </Button>
              ) : bucket === 'ACTIVE' ? (
                <Button asChild><Link to="/sell">방문견적 신청하기</Link></Button>
              ) : (
                <Button onClick={() => move('ACTIVE', null)}>진행 중 보기</Button>
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
                    {/* 보고 있던 자리를 함께 넘긴다. 상세의 "마이페이지"가 돌아올 곳을 이 값으로 정한다 */}
                    <Link to={detailPath(evaluation.evaluationId, bucket, state)}>
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

function detailPath(
  evaluationId: number,
  bucket: EvaluationRequestBucket,
  state: EvaluationRequestState | null,
): string {
  const params = new URLSearchParams()
  if (bucket !== DEFAULT_BUCKET) params.set(BUCKET_PARAM, bucket)
  if (state) params.set(STATE_PARAM, state)
  const query = params.toString()

  return `/mypage/evaluations/${evaluationId}${query ? `?${query}` : ''}`
}

/** 눌러 켜고 끄는 상태 하나. 경매 필터의 칩과 같은 모양이다 — 강조는 채도가 아니라 대비로 준다 */
function StateChip({
  selected,
  count,
  onClick,
  children,
}: {
  selected: boolean
  count: number
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      aria-pressed={selected}
      onClick={onClick}
      className={cn(
        'h-10 rounded-full border px-4 text-base whitespace-nowrap transition-colors',
        selected
          ? 'border-foreground bg-foreground text-background font-medium'
          : 'border-input text-foreground hover:border-foreground/60 hover:bg-accent',
      )}
    >
      {children}
      <span className={cn('tabular ml-1.5 text-sm', selected ? 'opacity-80' : 'text-muted-foreground')}>
        {count}
      </span>
    </button>
  )
}
