import type { DealerApplicationStatus } from './types'

export const DEALER_APPLICATIONS_QUERY_KEY = ['admin', 'dealer-applications'] as const

/**
 * 상태별 목록. 상태가 키에 들어가지 않으면 대기 목록의 캐시를 승인 탭이 그대로 재사용해,
 * 탭을 옮긴 첫 순간에 다른 상태의 목록이 보인다.
 *
 * 판정 뒤에는 목록 키를 접두사로 무효화한다. 한 건이 판정되면 대기에서 빠지고 승인·반려 쪽으로
 * 들어가므로 두 목록이 동시에 낡는다.
 */
export const dealerApplicationsQueryKey = (status: DealerApplicationStatus) =>
  [...DEALER_APPLICATIONS_QUERY_KEY, 'list', status] as const

/**
 * 신청 상세. 목록 키를 접두사로 삼지 않는다 — 상세에는 만료가 있는 사원증 주소가 들어 있어,
 * 목록이 낡을 때마다 함께 무효화되면 관리자가 보고 있던 이미지가 이유 없이 다시 불린다.
 */
export const dealerApplicationDetailQueryKey = (applicationId: number) =>
  ['admin', 'dealer-application', applicationId] as const
