import type {
  FuelType,
  Manufacturer,
  Transmission,
} from '@/features/quote/types'

export type EvaluationStatus =
  | 'REQUESTED'
  | 'APPROVED'
  | 'REJECTED'

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
}

export interface EvaluationResultRequest {
  mileage: number
  estimatedPrice: number
  imageUrls: string[]
  diagnosticReportUrl: string
}

export interface EvaluationResult extends EvaluationResultRequest {
  evaluationId: number
  vehicleId: number
  status: EvaluationStatus
  submittedAt: string
}

export type UploadContentType =
  | 'image/jpeg'
  | 'image/png'
  | 'image/webp'
  | 'application/pdf'

export interface PresignedUploadRequest {
  files: { contentType: UploadContentType; contentLength: number }[]
}

export interface PresignedUpload {
  key: string
  uploadUrl: string
  fileUrl: string
  expiresAt: string
}

export interface PresignedUploadResponse {
  uploads: PresignedUpload[]
}
