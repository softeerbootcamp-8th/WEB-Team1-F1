import { axiosInstance } from '@/lib/axios'
import type {
  AssignableEvaluationCountResponse,
  AssignableEvaluationCursor,
  AssignableEvaluationsResponse,
  EvaluationAssignment,
  EvaluationDetail,
  EvaluationRejection,
  EvaluationResult,
  EvaluationResultPatchRequest,
  EvaluationResultRequest,
  EvaluationSummariesResponse,
} from './types'

/**
 * 배정 대기 목록 한 페이지. 커서 없이 부르면 첫 페이지, 이후에는 직전 응답의 nextCursor를
 * 그대로 돌려보낸다(한쪽만 보내면 서버가 400).
 *
 * 커서는 목록에서 몇 번째인가가 아니라 정렬축(방문일·신청 ID) 위의 좌표다. 그래서 이어 읽는
 * 사이 다른 평가사가 앞의 신청을 수락해 목록에서 빠져도 남은 신청을 건너뛰지 않는다.
 */
export async function fetchAssignableEvaluations(
  cursor?: AssignableEvaluationCursor | null,
): Promise<AssignableEvaluationsResponse> {
  const params = new URLSearchParams()
  if (cursor) {
    params.set('visitDate', cursor.visitDate)
    params.set('evaluationId', String(cursor.evaluationId))
  }

  const { data } = await axiosInstance.get<AssignableEvaluationsResponse>(
    '/api/evaluations/assignable',
    { params },
  )
  return data
}

/** 배정 대기 전체 건수. 목록이 나뉘어 나가면서 첫 페이지 길이로는 셀 수 없게 됐다 */
export async function fetchAssignableEvaluationCount(): Promise<AssignableEvaluationCountResponse> {
  const { data } = await axiosInstance.get<AssignableEvaluationCountResponse>(
    '/api/evaluations/assignable/count',
  )
  return data
}

export async function assignEvaluation(
  evaluationId: number,
): Promise<EvaluationAssignment> {
  const { data } = await axiosInstance.post<EvaluationAssignment>(
    `/api/evaluations/${evaluationId}/assignment`,
  )
  return data
}

export async function fetchMyAssignments(): Promise<EvaluationSummariesResponse> {
  const { data } = await axiosInstance.get<EvaluationSummariesResponse>(
    '/api/evaluations/my-assignments',
  )
  return data
}

export async function fetchMyRequests(): Promise<EvaluationSummariesResponse> {
  const { data } = await axiosInstance.get<EvaluationSummariesResponse>(
    '/api/evaluations/my-requests',
  )
  return data
}

export async function fetchEvaluationDetail(
  evaluationId: number,
): Promise<EvaluationDetail> {
  const { data } = await axiosInstance.get<EvaluationDetail>(
    `/api/evaluations/${evaluationId}`,
  )
  return data
}

export async function submitEvaluationResult(
  evaluationId: number,
  request: EvaluationResultRequest,
): Promise<EvaluationResult> {
  const { data } = await axiosInstance.put<EvaluationResult>(
    `/api/evaluations/${evaluationId}/result`,
    request,
  )
  return data
}

export async function patchEvaluationResult(
  evaluationId: number,
  request: EvaluationResultPatchRequest,
): Promise<EvaluationResult> {
  const { data } = await axiosInstance.patch<EvaluationResult>(
    `/api/evaluations/${evaluationId}/result`,
    request,
  )
  return data
}

export async function rejectEvaluation(
  evaluationId: number,
  reason: string,
): Promise<EvaluationRejection> {
  const { data } = await axiosInstance.post<EvaluationRejection>(
    `/api/evaluations/${evaluationId}/rejection`,
    { reason },
  )
  return data
}
