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
