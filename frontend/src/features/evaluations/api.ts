import { axiosInstance } from '@/lib/axios'
import type {
  AssignableEvaluationsResponse,
  EvaluationAssignment,
  EvaluationDetail,
  EvaluationRejection,
  EvaluationResult,
  EvaluationResultPatchRequest,
  EvaluationResultRequest,
  EvaluationSummariesResponse,
} from './types'

export async function fetchAssignableEvaluations(): Promise<AssignableEvaluationsResponse> {
  const { data } = await axiosInstance.get<AssignableEvaluationsResponse>(
    '/api/evaluations/assignable',
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
