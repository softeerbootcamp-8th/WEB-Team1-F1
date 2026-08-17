import { axiosInstance } from '@/lib/axios'
import type {
  AssignableEvaluationCountResponse,
  AssignableEvaluationCursor,
  AssignableEvaluationSort,
  AssignableEvaluationsResponse,
  EvaluationAssignment,
  EvaluationAssignmentCounts,
  EvaluationAssignmentScope,
  EvaluationDetail,
  EvaluationRejection,
  EvaluationResult,
  EvaluationResultPatchRequest,
  EvaluationResultRequest,
  EvaluationSummariesResponse,
} from './types'

/**
 * 배정 대기 목록 한 페이지. 커서 없이 부르면 첫 페이지, 이후에는 직전 응답의 nextCursor를
 * 그대로 돌려보낸다.
 *
 * 커서는 목록에서 몇 번째인가가 아니라 정렬축 위의 좌표다. 그래서 이어 읽는 사이 다른 평가사가
 * 앞의 신청을 수락해 목록에서 빠져도 남은 신청을 건너뛰지 않는다.
 *
 * 커서에 담기는 값은 정렬이 정하므로 sort와 cursor는 같은 짝이어야 한다. 서버가 준 커서를 그대로
 * 돌려보내는 한 어긋나지 않고, 어긋난 짝은 400으로 돌아온다.
 */
export async function fetchAssignableEvaluations(
  sort: AssignableEvaluationSort,
  cursor?: AssignableEvaluationCursor | null,
): Promise<AssignableEvaluationsResponse> {
  const params = new URLSearchParams({ sort })
  if (cursor) {
    // 최신순 커서에는 방문일이 없다. 빈 값을 실어 보내면 정렬과 어긋난 요청이 된다
    if (cursor.visitDate !== null) params.set('visitDate', cursor.visitDate)
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

/**
 * 평가사가 맡은 신청들. 범위가 담기는 상태와 순서를 함께 정한다 — ACTIVE는 방문일 임박순,
 * COMPLETED는 최근 끝낸 순이다.
 *
 * 범위를 인자로 받는다. 기본값을 여기 두면 화면이 무엇을 보고 있는지가 두 곳에 적힌다.
 */
export async function fetchMyAssignments(
  scope: EvaluationAssignmentScope,
): Promise<EvaluationSummariesResponse> {
  const { data } = await axiosInstance.get<EvaluationSummariesResponse>(
    '/api/evaluations/my-assignments',
    { params: { scope } },
  )
  return data
}

/** 담당 건수. 목록이 범위로 갈린 뒤로는 어느 한쪽을 받아도 나머지를 셀 수 없다 */
export async function fetchMyAssignmentCounts(): Promise<EvaluationAssignmentCounts> {
  const { data } = await axiosInstance.get<EvaluationAssignmentCounts>(
    '/api/evaluations/my-assignments/count',
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
