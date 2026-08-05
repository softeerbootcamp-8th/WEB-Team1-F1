import { axiosInstance } from '@/lib/axios'
import type {
  VehicleLookupRequest,
  VehicleLookupResponse,
} from '@/features/vehicle/types'

/** 이름과 번호판으로 차량 제원만 확인한다. 로그인과 주행거리는 필요하지 않다. */
export async function lookupVehicle(
  request: VehicleLookupRequest,
): Promise<VehicleLookupResponse> {
  const { data } = await axiosInstance.post<VehicleLookupResponse>(
    '/api/vehicles/lookup',
    request,
  )
  return data
}
