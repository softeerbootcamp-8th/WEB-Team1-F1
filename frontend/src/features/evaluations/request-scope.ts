import type { EvaluationSummary } from './types'
import { canRegisterAuction } from './utils'

/**
 * 판매자의 "진단 신청 내역"이 무엇을 담을지. 기본은 진행 중이다.
 *
 * **평가사의 범위와 축이 다르다.** 그쪽은 "진단을 썼는가"로 가르지만, 판매자에게 진단 완료는
 * 끝이 아니라 출품이라는 할 일이 생기는 지점이다. 같은 기준을 쓰면 판매자가 가장 먼저 봐야 할
 * 건이 완료 칸으로 숨는다.
 *
 * 그래서 여기서 묻는 것은 "이 신청에 아직 내가 신경 쓸 것이 남았는가"이다.
 */
export type EvaluationRequestScope = 'ACTIVE' | 'CLOSED'

/**
 * 이 신청이 끝났는지.
 *
 * 끝나는 길은 둘뿐이다 — 반려되어 매물이 되지 못했거나, 낙찰되어 판매가 이루어졌거나.
 * 낙찰된 뒤의 일은 마이페이지의 "판매 내역" 탭이 거래로 이어받으므로 여기 남을 이유가 없다.
 *
 * 유찰(FAILED)은 끝이 아니다. 같은 진단 차량으로 다시 출품할 수 있어 오히려 판매자가 손을
 * 대야 하는 쪽이다.
 */
export function requestScopeOf(evaluation: EvaluationSummary): EvaluationRequestScope {
  if (evaluation.status === 'REJECTED') return 'CLOSED'
  if (evaluation.auctionStatus === 'ENDED') return 'CLOSED'
  return 'ACTIVE'
}

/**
 * 판매자가 지금 출품할 수 있는 건인지. 진단이 끝났는데 걸린 경매가 없거나 유찰된 경우다.
 *
 * 판정을 {@link canRegisterAuction}에서 가져온다. 출품 버튼을 여닫는 기준과 목록에서 끌어올리는
 * 기준이 갈라지면, 위로 올라온 카드에 들어갔더니 등록할 수 없다는 안내만 있는 일이 생긴다.
 */
export function needsListing(evaluation: EvaluationSummary): boolean {
  return evaluation.status === 'APPROVED' && canRegisterAuction(evaluation.auctionStatus)
}

/**
 * 한 범위에 담기는 신청들. 진행 중에서는 출품할 수 있는 건이 맨 위로 온다.
 *
 * 그 안의 순서는 서버가 준 접수 최신순 그대로다(정렬이 안정적이라 유지된다). 판매자가 목록에서
 * 하는 판단은 "지금 할 게 있나"와 "언제 낸 신청인가" 둘뿐이라, 그 위에 순서를 더 얹지 않는다.
 */
export function selectRequests(
  evaluations: EvaluationSummary[],
  scope: EvaluationRequestScope,
): EvaluationSummary[] {
  const selected = evaluations.filter((evaluation) => requestScopeOf(evaluation) === scope)
  if (scope === 'CLOSED') return selected

  return [...selected].sort(
    (a, b) => Number(needsListing(b)) - Number(needsListing(a)),
  )
}

/** 탭에 함께 보여줄 건수. 목록을 통째로 받으므로 따로 세지 않아도 나온다 */
export function countRequests(
  evaluations: EvaluationSummary[],
  scope: EvaluationRequestScope,
): number {
  return evaluations.filter((evaluation) => requestScopeOf(evaluation) === scope).length
}
