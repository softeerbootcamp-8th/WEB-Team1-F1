import type {
  FuelType,
  Manufacturer,
  Transmission,
  VehicleKeyword,
} from '@/features/quote/types'

export type EvaluationStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'REJECTED'

export type EvaluationAuctionStatus =
  | 'SCHEDULED'
  | 'IN_PROGRESS'
  | 'ENDED'
  | 'FAILED'

export interface AssignableEvaluation {
  evaluationId: number
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  fuelType: FuelType
  transmission: Transmission
  visitDate: string
  visitAddress: string
  requestedAt: string
}

/**
 * 직전 페이지가 끝난 지점. 서버가 준 값을 그대로 돌려보낸다.
 *
 * 목록 정렬이 방문일 순인데 방문일은 날짜 단위라 같은 값이 페이지 크기를 넘길 만큼 몰린다.
 * 그래서 신청 ID가 함께 간다 — 한쪽만 보내면 서버가 400으로 막는다.
 */
export interface AssignableEvaluationCursor {
  visitDate: string
  evaluationId: number
}

export interface AssignableEvaluationsResponse {
  evaluations: AssignableEvaluation[]
  hasNext: boolean
  /** 마지막 페이지면 null */
  nextCursor: AssignableEvaluationCursor | null
}

/** 배정 대기 전체 건수. 첫 페이지만 받는 평가사 홈이 이 값으로 건수를 보여준다 */
export interface AssignableEvaluationCountResponse {
  count: number
}

export interface EvaluationAssignment {
  evaluationId: number
  plateNumber: string
  visitDate: string
  visitAddress: string
  contactPhone: string
  status: EvaluationStatus
}

export interface EvaluationSummary {
  evaluationId: number
  status: EvaluationStatus
  assigned: boolean
  auctionStatus?: EvaluationAuctionStatus | null
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  visitDate: string
  visitAddress: string
  requestedAt: string
}

export interface EvaluationSummariesResponse {
  evaluations: EvaluationSummary[]
}

export interface EvaluationDetail {
  evaluationId: number
  status: EvaluationStatus
  visitDate: string
  visitAddress: string
  contactPhone: string
  requestedAt: string
  evaluatorName: string | null
  vehicleId: number
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  fuelType: FuelType
  transmission: Transmission
  mileage: number | null
  estimatedPrice: number | null
  imageUrls: string[]
  diagnosticReportUrl: string | null
  submittedAt: string | null
  keywords: VehicleKeyword[]
  rejectReason: string | null
}

export interface EvaluationResultRequest {
  mileage: number
  estimatedPrice: number
  imageUrls: string[]
  diagnosticReportUrl: string
  keywords: VehicleKeyword[]
}

export interface EvaluationResultPatchRequest {
  mileage?: number
  estimatedPrice?: number
  imageUrls?: string[]
  diagnosticReportUrl?: string
  keywords?: VehicleKeyword[]
}

export interface EvaluationResult extends EvaluationResultRequest {
  evaluationId: number
  vehicleId: number
  status: EvaluationStatus
  submittedAt: string
}

export interface EvaluationRejection {
  evaluationId: number
  status: 'REJECTED'
  rejectReason: string
  rejectedAt: string
}

// 업로드 형식과 발급 계약은 `@/lib/upload` 로 옮겼다. 진단서와 거래 서류가 같은 경로를 쓴다.
