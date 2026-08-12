import type {
  FuelType,
  Manufacturer,
  Transmission,
} from '@/features/quote/types'

/** POST /api/vehicles/lookup 요청 계약 */
export interface VehicleLookupRequest {
  plateNumber: string
  ownerName: string
}

/**
 * GET /api/vehicles/demo 200 응답 계약. 최대 10대까지 온다.
 *
 * 기준가와 대표 이미지는 서버가 내려주지 않는다 — 기준가는 예상 시세와 나란히 놓이면 감가율이
 * 역산되고, 이미지는 도움말 표가 쓰지 않는다.
 */
export interface DemoVehicle {
  plateNumber: string
  ownerName: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
}

/** POST /api/vehicles/lookup 200 응답 계약 */
export interface VehicleLookupResponse {
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  fuelType: FuelType
  transmission: Transmission
  mainImageUrl: string | null
}
