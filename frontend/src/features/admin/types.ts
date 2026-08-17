import type { UserRole } from '@/types/domain'

/** 백엔드 DealerApplicationStatus와 1:1 */
export type DealerApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** 목록의 한 건. 사원증 주소는 상세에만 있다 */
export interface DealerApplicationSummary {
  id: number
  applicantId: number
  username: string
  realName: string
  status: DealerApplicationStatus
  appliedAt: string
}

export interface DealerApplicationsResponse {
  applications: DealerApplicationSummary[]
}

export interface DealerApplicationDetail {
  id: number
  applicantId: number
  username: string
  realName: string
  email: string
  phone: string
  status: DealerApplicationStatus
  rejectReason: string | null
  appliedAt: string
  /** 서명된 임시 주소. 만료되면 상세를 다시 조회해 새로 받는다 */
  licenseViewUrl: string
  /** image/jpeg · image/png · application/pdf. 사원증은 셋 다 받으므로 이 값으로 뷰어를 가른다 */
  licenseContentType: string
  licenseViewExpiresAt: string
}

export interface DealerApplicationDecision {
  id: number
  status: DealerApplicationStatus
  rejectReason: string | null
}

/** 백엔드 DealerApplication.MAX_REJECT_REASON_LENGTH와 같은 값 */
export const MAX_REJECT_REASON_LENGTH = 500

export const DEALER_APPLICATION_STATUS_LABEL: Record<DealerApplicationStatus, string> = {
  PENDING: '심사 대기',
  APPROVED: '승인',
  REJECTED: '반려',
}

// ================= 회원 관리 =================

/** 백엔드 UserStatus와 1:1. 역할과 직교해서, 정지된 딜러도 role은 그대로 DEALER다 */
export type UserStatus = 'ACTIVE' | 'SUSPENDED'

/** 목록의 한 줄. 이메일·연락처·정지 사유는 서버가 상세에만 담아 준다 */
export interface UserSummary {
  id: number
  username: string
  realName: string
  role: UserRole
  status: UserStatus
  joinedAt: string
}

export interface UsersResponse {
  users: UserSummary[]
  /** 0부터 세는 현재 페이지. 화면 표기(1부터)는 이 값에 1을 더한다 */
  page: number
  /** 조건에 맞는 회원이 없으면 0 */
  totalPages: number
  totalUsers: number
}

export interface UserDetail extends UserSummary {
  email: string
  phone: string
  /** 이용 중이면 null */
  suspendReason: string | null
}

/** 정지·해제 직후의 이용 상태. 역할이 함께 오는 것은 정지가 역할을 바꾸지 않기 때문이다 */
export interface UserStatusResult {
  id: number
  role: UserRole
  status: UserStatus
  suspendReason: string | null
}

/** 목록 조회 조건. 비어 있는 값은 조건을 걸지 않는다 */
export interface UserSearchCondition {
  keyword: string
  role: UserRole | null
  status: UserStatus | null
  page: number
}

/** 백엔드 User.MAX_SUSPEND_REASON_LENGTH와 같은 값 */
export const MAX_SUSPEND_REASON_LENGTH = 500

export const USER_STATUS_LABEL: Record<UserStatus, string> = {
  ACTIVE: '이용 중',
  SUSPENDED: '이용 정지',
}
