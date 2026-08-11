import { axiosInstance } from '@/lib/axios'
import type {
  AssignableEvaluationsResponse,
  EvaluationAssignment,
  EvaluationDetail,
  EvaluationResult,
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
