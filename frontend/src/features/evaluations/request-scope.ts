import type { EvaluationSummary } from './types'
import { canRegisterAuction } from './utils'

/**
 * 판매자가 보는 신청 하나의 처지.
 *
 * **평가사의 범위와 축이 다르다.** 그쪽은 "진단을 썼는가"로 가르지만, 판매자에게 진단 완료는
 * 끝이 아니라 출품이라는 할 일이 생기는 지점이다. 같은 기준을 쓰면 판매자가 가장 먼저 봐야 할
 * 건이 완료 칸으로 숨는다.
 *
 * 그래서 여기서 묻는 것은 "이 신청이 지금 누구의 손에 있는가"이다 — 평가사를 기다리는지,
 * 판매자가 움직여야 하는지, 경매가 답할 차례인지, 아니면 끝났는지.
 */
export type EvaluationRequestState =
  | 'PENDING_ASSIGNMENT'
  | 'EVALUATING'
  | 'READY_TO_LIST'
  | 'IN_AUCTION'
  | 'CLOSED'

/**
 * 목록이 보여줄 범위. 상태 다섯에 더해 {@link ACTIVE_SCOPE}가 있다.
 *
 * <b>요약 하나를 앞에 두는 이유.</b> 다섯이 배타적이기만 하면 기본으로 열리는 칸이 대개 비어
 * 있고(배정 대기는 신청 직후 잠깐 머무는 상태다), 진행 중인 신청 전체를 훑으려면 칸을 네 번
 * 옮겨야 한다. 종료만 빼고 모으는 칸이 기본이면 들어오자마자 남은 일이 다 보이고, 좁혀 보고
 * 싶을 때만 상태를 고르면 된다.
 */
export type EvaluationRequestScope = typeof ACTIVE_SCOPE | EvaluationRequestState

/** 종료를 뺀 전체. 목록의 기본 화면이다 */
export const ACTIVE_SCOPE = 'ACTIVE'

const SCOPES: EvaluationRequestScope[] = [
  ACTIVE_SCOPE,
  'PENDING_ASSIGNMENT',
  'EVALUATING',
  'READY_TO_LIST',
  'IN_AUCTION',
  'CLOSED',
]

/**
 * 이 신청이 지금 어느 처지인지.
 *
 * 끝나는 길은 둘뿐이다 — 반려되어 매물이 되지 못했거나, 낙찰되어 판매가 이루어졌거나.
 * 낙찰 뒤의 일은 마이페이지의 "판매 내역" 탭이 거래로 이어받으므로 여기 남을 이유가 없다.
 *
 * 유찰(FAILED)은 끝이 아니라 {@code READY_TO_LIST}다. 같은 진단 차량으로 다시 출품할 수 있어
 * 오히려 판매자가 손을 대야 하는 쪽이다. 그 판정을 {@link canRegisterAuction}에서 가져오는 것은
 * 출품 버튼을 여닫는 기준과 갈라지지 않게 하기 위해서다 — 갈라지면 "출품 대기"로 올라온 카드에
 * 들어갔더니 등록할 수 없다는 안내만 있는 일이 생긴다.
 *
 * 상태를 먼저 보고 경매를 나중에 본다. 반려된 신청의 차량에도 지난 경매 이력이 남아 있을 수
 * 있어, 경매부터 보면 끝난 신청이 경매 칸으로 새어 나간다.
 */
export function requestStateOf(evaluation: EvaluationSummary): EvaluationRequestState {
  if (evaluation.status === 'REJECTED') return 'CLOSED'
  if (evaluation.auctionStatus === 'ENDED') return 'CLOSED'
  if (evaluation.status === 'APPROVED') {
    return canRegisterAuction(evaluation.auctionStatus) ? 'READY_TO_LIST' : 'IN_AUCTION'
  }

  // 배정은 상태를 바꾸지 않는다(배정과 평가 결과가 다른 축이라는 설계). 그래서 평가사가
  // 정해졌는지는 assigned로만 알 수 있다
  return evaluation.assigned ? 'EVALUATING' : 'PENDING_ASSIGNMENT'
}

/** 판매자가 지금 출품할 수 있는 건인지. 목록이 위로 끌어올리고 배지를 붙이는 기준이다 */
export function needsListing(evaluation: EvaluationSummary): boolean {
  return requestStateOf(evaluation) === 'READY_TO_LIST'
}

export function isRequestScope(value: string): value is EvaluationRequestScope {
  return (SCOPES as string[]).includes(value)
}

function matchesScope(evaluation: EvaluationSummary, scope: EvaluationRequestScope): boolean {
  const state = requestStateOf(evaluation)

  return scope === ACTIVE_SCOPE ? state !== 'CLOSED' : state === scope
}

/**
 * 한 범위에 담기는 신청들. 진행 중에서는 출품할 수 있는 건이 맨 위로 온다.
 *
 * 그 안의 순서는 서버가 준 접수 최신순 그대로다(정렬이 안정적이라 유지된다). 판매자가 목록에서
 * 하는 판단은 "지금 할 게 있나"와 "언제 낸 신청인가" 둘뿐이라, 그 위에 순서를 더 얹지 않는다.
 * 상태 하나만 담기는 칸에서는 어차피 순서가 움직이지 않는다.
 */
export function selectRequests(
  evaluations: EvaluationSummary[],
  scope: EvaluationRequestScope,
): EvaluationSummary[] {
  return evaluations
    .filter((evaluation) => matchesScope(evaluation, scope))
    .sort((a, b) => Number(needsListing(b)) - Number(needsListing(a)))
}

/** 탭에 함께 보여줄 건수. 목록을 통째로 받으므로 따로 세지 않아도 나온다 */
export function countRequests(
  evaluations: EvaluationSummary[],
  scope: EvaluationRequestScope,
): number {
  return evaluations.filter((evaluation) => matchesScope(evaluation, scope)).length
}
