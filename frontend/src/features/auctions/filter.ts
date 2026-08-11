import type { FuelType, Manufacturer, Transmission } from '@/features/quote/types'
import { FUEL_TYPE_LABEL, MANUFACTURER_LABEL, TRANSMISSION_LABEL } from '@/features/quote/types'

/**
 * GET /api/auctions 의 차량·가격 조건 쿼리 파라미터와 1:1. null·빈 배열은 조건 없음이다.
 * 제조사·변속기는 화면이 단일 선택이라 단수, 연료만 다중 선택이다.
 */
export interface AuctionVehicleFilter {
  manufacturer: Manufacturer | null
  fuelTypes: FuelType[]
  transmission: Transmission | null
  mileageMin: number | null
  mileageMax: number | null
  modelYearMin: number | null
  modelYearMax: number | null
  priceMin: number | null
  priceMax: number | null
}

export const EMPTY_FILTER: AuctionVehicleFilter = {
  manufacturer: null,
  fuelTypes: [],
  transmission: null,
  mileageMin: null,
  mileageMax: null,
  modelYearMin: null,
  modelYearMax: null,
  priceMin: null,
  priceMax: null,
}

/** 조건이 하나라도 걸려 있는지. 초기화 버튼 노출과 빈 화면 문구 분기가 쓴다. */
export function hasActiveFilter(filter: AuctionVehicleFilter): boolean {
  return countActiveFilters(filter) > 0
}

/** 걸려 있는 조건 수. 범위는 min/max 를 합쳐 한 종류로 센다. */
export function countActiveFilters(filter: AuctionVehicleFilter): number {
  let count = 0
  if (filter.manufacturer) count += 1
  if (filter.fuelTypes.length > 0) count += 1
  if (filter.transmission) count += 1
  if (filter.mileageMin !== null || filter.mileageMax !== null) count += 1
  if (filter.modelYearMin !== null || filter.modelYearMax !== null) count += 1
  if (filter.priceMin !== null || filter.priceMax !== null) count += 1
  return count
}

/**
 * 필터를 주소 쿼리에 싣는다. 파라미터 이름을 백엔드 계약과 같게 두어
 * 주소만 봐도 어떤 API 요청이 나가는지 읽히게 한다.
 */
export function writeFilterParams(filter: AuctionVehicleFilter, params: URLSearchParams): void {
  for (const key of FILTER_PARAM_KEYS) params.delete(key)

  if (filter.manufacturer) params.set('manufacturer', filter.manufacturer)
  for (const fuel of filter.fuelTypes) params.append('fuelTypes', fuel)
  if (filter.transmission) params.set('transmission', filter.transmission)
  setNumber(params, 'mileageMin', filter.mileageMin)
  setNumber(params, 'mileageMax', filter.mileageMax)
  setNumber(params, 'modelYearMin', filter.modelYearMin)
  setNumber(params, 'modelYearMax', filter.modelYearMax)
  setNumber(params, 'priceMin', filter.priceMin)
  setNumber(params, 'priceMax', filter.priceMax)
}

/** 주소 쿼리에서 필터를 복원한다. 손으로 고친 잘못된 값은 조건 없음으로 떨어뜨린다. */
export function readFilterParams(params: URLSearchParams): AuctionVehicleFilter {
  return {
    manufacturer: readEnum(params.get('manufacturer'), MANUFACTURER_LABEL),
    fuelTypes: params.getAll('fuelTypes').flatMap((raw) => {
      const fuel = readEnum(raw, FUEL_TYPE_LABEL)
      return fuel ? [fuel] : []
    }),
    transmission: readEnum(params.get('transmission'), TRANSMISSION_LABEL),
    mileageMin: readNumber(params.get('mileageMin')),
    mileageMax: readNumber(params.get('mileageMax')),
    modelYearMin: readNumber(params.get('modelYearMin')),
    modelYearMax: readNumber(params.get('modelYearMax')),
    priceMin: readNumber(params.get('priceMin')),
    priceMax: readNumber(params.get('priceMax')),
  }
}

/** 조회 요청에 실을 파라미터. 이름이 주소 쿼리와 같아 화면 주소가 곧 요청이다. */
export function toFilterParams(filter: AuctionVehicleFilter): URLSearchParams {
  const params = new URLSearchParams()
  writeFilterParams(filter, params)
  return params
}

/**
 * 값이 같으면 같은 문자열. 목록 캐시 키와 재조회 판단에 쓴다 —
 * 객체 동일성으로 보면 렌더마다 다른 목록으로 읽혀 매번 첫 페이지부터 다시 읽는다.
 */
export function filterKey(filter: AuctionVehicleFilter): string {
  const params = toFilterParams(filter)
  params.sort()
  return params.toString()
}

const FILTER_PARAM_KEYS = [
  'manufacturer',
  'fuelTypes',
  'transmission',
  'mileageMin',
  'mileageMax',
  'modelYearMin',
  'modelYearMax',
  'priceMin',
  'priceMax',
] as const

function setNumber(params: URLSearchParams, key: string, value: number | null): void {
  if (value !== null) params.set(key, String(value))
}

function readNumber(raw: string | null): number | null {
  if (raw === null) return null
  const value = Number(raw)
  return Number.isSafeInteger(value) && value >= 0 ? value : null
}

// 라벨 맵의 키가 곧 유효한 enum 값 목록이라 별도 상수 없이 검증에 쓴다.
function readEnum<T extends string>(raw: string | null, labels: Record<T, string>): T | null {
  return raw !== null && raw in labels ? (raw as T) : null
}
