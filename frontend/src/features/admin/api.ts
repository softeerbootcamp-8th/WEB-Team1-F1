import { axiosInstance } from '@/lib/axios'
import type {
  DealerApplicationDecision,
  DealerApplicationDetail,
  DealerApplicationStatus,
  DealerApplicationsResponse,
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
