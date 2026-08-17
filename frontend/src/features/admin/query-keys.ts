import type { DealerApplicationStatus, UserSearchCondition } from './types'

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

export const ADMIN_USERS_QUERY_KEY = ['admin', 'users'] as const

/**
 * 조건별 회원 목록. 검색어·필터·페이지가 모두 키에 들어가야 한다 — 하나라도 빠지면 조건을 바꾼
 * 첫 순간에 이전 조건의 목록이 그대로 보인다(딜러 심사 목록이 상태를 키에 넣는 것과 같은 이유다).
 *
 * 정지·해제 뒤에는 목록 키를 접두사로 무효화한다. 그 회원이 "이용 중" 목록에서 빠지고 "정지"
 * 목록으로 들어가므로 조건이 다른 여러 목록이 한꺼번에 낡는다.
 */
export const adminUsersQueryKey = (condition: UserSearchCondition) =>
  [
    ...ADMIN_USERS_QUERY_KEY,
    'list',
    condition.keyword.trim(),
    condition.role,
    condition.status,
    condition.page,
  ] as const

/**
 * 회원 상세. 목록 키를 접두사로 삼아, 정지·해제 뒤에 열려 있는 상세도 함께 낡게 한다 —
 * 딜러 심사 상세와 달리 만료되는 서명 주소가 없어 함께 무효화해도 잃을 것이 없고,
 * 다이얼로그를 열어 둔 채 조치하면 그 안의 상태 표시가 그대로 갱신돼야 한다.
 */
export const adminUserDetailQueryKey = (userId: number) =>
  [...ADMIN_USERS_QUERY_KEY, 'detail', userId] as const
