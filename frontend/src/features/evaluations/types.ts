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

export interface AssignableEvaluationsResponse {
  evaluations: AssignableEvaluation[]
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
