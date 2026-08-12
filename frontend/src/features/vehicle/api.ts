import { axiosInstance } from '@/lib/axios'
import type {
  DemoVehicle,
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

/**
 * GET /api/vehicles/demo. 넣어 볼 수 있는 데모 차량만 온다 — 이미 신청·승인된 차량은 서버가
 * 뺀 뒤에 내려주므로 화면이 다시 거르지 않는다. 로그인이 필요하지 않다.
 */
export async function fetchDemoVehicles(): Promise<DemoVehicle[]> {
  const { data } = await axiosInstance.get<DemoVehicle[]>('/api/vehicles/demo')
  return data
}
