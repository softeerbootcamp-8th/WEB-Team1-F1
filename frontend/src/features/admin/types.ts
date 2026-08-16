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
