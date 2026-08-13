/**
 * 시세 조회 응답 계약. 백엔드 QuoteResponse에 맞춰 두고,
 * 실서비스 연동 시 orval 생성 타입으로 교체한다.
 */

export type Manufacturer =
  | 'HYUNDAI'
  | 'KIA'
  | 'GENESIS'
  | 'CHEVROLET'
  | 'RENAULT_KOREA'
  | 'KG_MOBILITY'
  | 'BMW'
  | 'MERCEDES_BENZ'
  | 'AUDI'
  | 'VOLKSWAGEN'
  | 'VOLVO'
  | 'TOYOTA'
  | 'LEXUS'
  | 'HONDA'
  | 'NISSAN'
  | 'FORD'
  | 'TESLA'
  | 'MINI'
  | 'PORSCHE'
  | 'LAND_ROVER'
  | 'JEEP'
  | 'PEUGEOT'

export type FuelType = 'GASOLINE' | 'DIESEL' | 'LPG' | 'HYBRID' | 'ELECTRIC' | 'HYDROGEN'

export type Transmission = 'AUTOMATIC' | 'MANUAL'

export interface QuoteResult {
  plateNumber: string
  manufacturer: Manufacturer
  model: string
  modelYear: number
  mileage: number
  fuelType: FuelType
  mainImageUrl: string | null
  estimatedPrice: number
}

export const MANUFACTURER_LABEL: Record<Manufacturer, string> = {
  HYUNDAI: '현대',
  KIA: '기아',
  GENESIS: '제네시스',
  CHEVROLET: '쉐보레',
  RENAULT_KOREA: '르노코리아',
  KG_MOBILITY: 'KG모빌리티',
  BMW: 'BMW',
  MERCEDES_BENZ: '벤츠',
  AUDI: '아우디',
  VOLKSWAGEN: '폭스바겐',
  VOLVO: '볼보',
  TOYOTA: '토요타',
  LEXUS: '렉서스',
  HONDA: '혼다',
  NISSAN: '닛산',
  FORD: '포드',
  TESLA: '테슬라',
  MINI: '미니',
  PORSCHE: '포르쉐',
  LAND_ROVER: '랜드로버',
  JEEP: '지프',
  PEUGEOT: '푸조',
}

export const FUEL_TYPE_LABEL: Record<FuelType, string> = {
  GASOLINE: '가솔린',
  DIESEL: '디젤',
  LPG: 'LPG',
  HYBRID: '하이브리드',
  ELECTRIC: '전기',
  HYDROGEN: '수소',
}

export const TRANSMISSION_LABEL: Record<Transmission, string> = {
  AUTOMATIC: '자동',
  MANUAL: '수동',
}

/** 평가사가 진단에서 확인한 차량 상태 키워드. 순서가 곧 표시 순서다(백엔드 VehicleKeyword 선언 순서와 동일) */
export type VehicleKeyword =
  | 'ACCIDENT_FREE'
  | 'MINOR_EXCHANGE'
  | 'NO_LEAK'
  | 'NO_DAMAGE'
  | 'UNDERBODY_INTACT'
  | 'GOOD_TIRE'
  | 'CLEAN_INTERIOR'

export const VEHICLE_KEYWORD_LABEL: Record<VehicleKeyword, string> = {
  ACCIDENT_FREE: '완전무사고',
  MINOR_EXCHANGE: '단순교환 무사고',
  NO_LEAK: '누유 없음',
  NO_DAMAGE: '파손 없음',
  UNDERBODY_INTACT: '하부 상태 양호',
  GOOD_TIRE: '타이어 상태 양호',
  CLEAN_INTERIOR: '실내 상태 양호',
}
