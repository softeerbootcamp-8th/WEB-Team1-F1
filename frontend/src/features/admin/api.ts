import { axiosInstance } from '@/lib/axios'
import type {
  DealerApplicationDecision,
  DealerApplicationDetail,
  DealerApplicationStatus,
  DealerApplicationsResponse,
  UserDetail,
  UserSearchCondition,
  UserStatusResult,
  UsersResponse,
} from './types'

/**
 * 심사 신청 목록. 서버가 나누어 주지 않고 그 상태의 전량을 접수 순으로 돌려준다 —
 * 대기 건수가 한 화면을 크게 넘길 서비스가 아니라 커서 페이징을 두지 않았다.
 */
export async function fetchDealerApplications(
  status: DealerApplicationStatus,
): Promise<DealerApplicationsResponse> {
  const { data } = await axiosInstance.get<DealerApplicationsResponse>(
    '/api/admin/dealer-applications',
    { params: { status } },
  )
  return data
}

/**
 * 신청 상세. 사원증을 볼 임시 주소가 이 응답에 담겨 오고, 그 주소에는 만료가 있다.
 * 오래 열어 둔 화면에서 이미지가 깨지면 이 조회를 다시 하면 된다.
 */
export async function fetchDealerApplicationDetail(
  applicationId: number,
): Promise<DealerApplicationDetail> {
  const { data } = await axiosInstance.get<DealerApplicationDetail>(
    `/api/admin/dealer-applications/${applicationId}`,
  )
  return data
}

export async function approveDealerApplication(
  applicationId: number,
): Promise<DealerApplicationDecision> {
  const { data } = await axiosInstance.post<DealerApplicationDecision>(
    `/api/admin/dealer-applications/${applicationId}/approval`,
  )
  return data
}

export async function rejectDealerApplication(
  applicationId: number,
  reason: string,
): Promise<DealerApplicationDecision> {
  const { data } = await axiosInstance.post<DealerApplicationDecision>(
    `/api/admin/dealer-applications/${applicationId}/rejection`,
    { reason },
  )
  return data
}

/**
 * 회원 목록. 페이지 크기는 서버가 고정하므로 여기서 정하지 않는다.
 *
 * 빈 값은 파라미터째 빼서 보낸다. `keyword=` 를 실어 보내면 서버가 그 빈 문자열을 조건으로
 * 오해할 여지를 남기는데, 아예 보내지 않으면 "조건 없음"이라는 뜻이 한 가지로만 읽힌다.
 */
export async function fetchUsers(condition: UserSearchCondition): Promise<UsersResponse> {
  const { data } = await axiosInstance.get<UsersResponse>('/api/admin/users', {
    params: {
      keyword: condition.keyword.trim() || undefined,
      role: condition.role ?? undefined,
      status: condition.status ?? undefined,
      page: condition.page,
    },
  })
  return data
}

/** 회원 상세. 목록에 없는 이메일·연락처·정지 사유가 여기에 있다 */
export async function fetchUserDetail(userId: number): Promise<UserDetail> {
  const { data } = await axiosInstance.get<UserDetail>(`/api/admin/users/${userId}`)
  return data
}

/** 정지는 그 회원의 세션까지 끊는다. 당사자는 즉시 로그아웃되고 다시 로그인할 수도 없다 */
export async function suspendUser(userId: number, reason: string): Promise<UserStatusResult> {
  const { data } = await axiosInstance.post<UserStatusResult>(
    `/api/admin/users/${userId}/suspension`,
    { reason },
  )
  return data
}

export async function activateUser(userId: number): Promise<UserStatusResult> {
  const { data } = await axiosInstance.post<UserStatusResult>(
    `/api/admin/users/${userId}/activation`,
  )
  return data
}
