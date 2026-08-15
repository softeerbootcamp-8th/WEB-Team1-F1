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
  | 'AUCTION_SCHEDULED'
  | 'IN_AUCTION'
  | 'REJECTED'
  | 'SOLD'

/**
 * 목록의 큰 틀. 여섯 상태가 이 둘 중 하나에 속한다.
 *
 * <b>층을 둘로 나눈 이유.</b> 상태 여섯을 한 줄에 늘어놓으면 무엇이 무엇을 품는지 화면에
 * 드러나지 않는다 — "진행 중"과 "출품 대기"가 같은 크기로 나란히 서면 둘이 형제로 읽힌다.
 * 큰 틀을 탭으로 먼저 고르고 그 안에서 상태로 좁히면, 고르는 순서가 곧 위계가 된다.
 */
export type EvaluationRequestBucket = 'ACTIVE' | 'CLOSED'

export const DEFAULT_BUCKET: EvaluationRequestBucket = 'ACTIVE'

/**
 * 큰 틀에 담기는 상태들. 순서가 곧 화면의 칩 순서이고, 신청이 실제로 거쳐 가는 순서다.
 *
 * 끝나는 길은 둘뿐이다 — 반려되어 매물이 되지 못했거나, 낙찰되어 판매가 이루어졌거나.
 * 낙찰 뒤의 일은 마이페이지의 "판매 내역" 탭이 거래로 이어받으므로 여기 남을 이유가 없다.
 */
export const STATES_BY_BUCKET: Record<EvaluationRequestBucket, EvaluationRequestState[]> = {
  ACTIVE: ['PENDING_ASSIGNMENT', 'EVALUATING', 'READY_TO_LIST', 'AUCTION_SCHEDULED', 'IN_AUCTION'],
  CLOSED: ['REJECTED', 'SOLD'],
}

/**
 * 이 신청이 지금 어느 처지인지.
 *
 * <b>경매 예정과 진행 중을 한 칸에 담지 않는다.</b> 판매자가 할 일이 없다는 점은 같지만 화면이
 * 두 칸에 다른 이름을 붙이는 이상 담기는 것도 달라야 한다 — "경매 중"을 눌렀는데 아직 시작하지
 * 않은 경매가 섞여 나오면 목록이 라벨을 배반한다. 시작 전에는 시작가와 시각을 고칠 수 있어
 * 판매자가 하는 판단도 실제로 다르다.
 * <p>
 * 유찰(FAILED)은 끝이 아니라 {@code READY_TO_LIST}다. 같은 진단 차량으로 다시 출품할 수 있어
 * 오히려 판매자가 손을 대야 하는 쪽이다. 그 판정을 {@link canRegisterAuction}에서 가져오는 것은
 * 출품 버튼을 여닫는 기준과 갈라지지 않게 하기 위해서다 — 갈라지면 "출품 대기"로 올라온 카드에
 * 들어갔더니 등록할 수 없다는 안내만 있는 일이 생긴다.
 *
 * 상태를 먼저 보고 경매를 나중에 본다. 반려된 신청의 차량에도 지난 경매 이력이 남아 있을 수
 * 있어, 경매부터 보면 끝난 신청이 경매 칸으로 새어 나간다.
 */
export function requestStateOf(evaluation: EvaluationSummary): EvaluationRequestState {
  if (evaluation.status === 'REJECTED') return 'REJECTED'
  if (evaluation.auctionStatus === 'ENDED') return 'SOLD'
  if (evaluation.status === 'APPROVED') {
    if (canRegisterAuction(evaluation.auctionStatus)) return 'READY_TO_LIST'

    return evaluation.auctionStatus === 'SCHEDULED' ? 'AUCTION_SCHEDULED' : 'IN_AUCTION'
  }

  // 배정은 상태를 바꾸지 않는다(배정과 평가 결과가 다른 축이라는 설계). 그래서 평가사가
  // 정해졌는지는 assigned로만 알 수 있다
  return evaluation.assigned ? 'EVALUATING' : 'PENDING_ASSIGNMENT'
}

export function bucketOf(state: EvaluationRequestState): EvaluationRequestBucket {
  return STATES_BY_BUCKET.CLOSED.includes(state) ? 'CLOSED' : 'ACTIVE'
}

/** 판매자가 지금 출품할 수 있는 건인지. 목록이 위로 끌어올리고 배지를 붙이는 기준이다 */
export function needsListing(evaluation: EvaluationSummary): boolean {
  return requestStateOf(evaluation) === 'READY_TO_LIST'
}

export function isBucket(value: string): value is EvaluationRequestBucket {
  return value === 'ACTIVE' || value === 'CLOSED'
}

/**
 * 주소에 실린 상태가 지금 고른 큰 틀의 것인지. 어긋나면 상태를 버린다.
 *
 * 두 값을 따로 싣기 때문에 `scope=ACTIVE&state=REJECTED`처럼 짝이 맞지 않는 주소가 만들어질 수
 * 있다. 그대로 두면 어느 칩도 켜지지 않은 채 빈 목록만 나와, 화면이 왜 비었는지 말하지 못한다.
 */
export function isStateOf(value: string, bucket: EvaluationRequestBucket): boolean {
  return (STATES_BY_BUCKET[bucket] as string[]).includes(value)
}

/**
 * 한 칸에 담기는 신청들. 상태를 고르지 않으면 그 큰 틀 전체다.
 *
 * 진행 중에서는 출품할 수 있는 건이 맨 위로 온다. 그 안의 순서는 서버가 준 접수 최신순
 * 그대로다(정렬이 안정적이라 유지된다). 판매자가 목록에서 하는 판단은 "지금 할 게 있나"와
 * "언제 낸 신청인가" 둘뿐이라, 그 위에 순서를 더 얹지 않는다.
 */
export function selectRequests(
  evaluations: EvaluationSummary[],
  bucket: EvaluationRequestBucket,
  state: EvaluationRequestState | null = null,
): EvaluationSummary[] {
  return evaluations
    .filter((evaluation) => {
      const current = requestStateOf(evaluation)

      return state ? current === state : bucketOf(current) === bucket
    })
    .sort((a, b) => Number(needsListing(b)) - Number(needsListing(a)))
}

/** 탭과 칩에 함께 보여줄 건수. 목록을 통째로 받으므로 따로 세지 않아도 나온다 */
export function countRequests(
  evaluations: EvaluationSummary[],
  bucket: EvaluationRequestBucket,
  state: EvaluationRequestState | null = null,
): number {
  return selectRequests(evaluations, bucket, state).length
}
