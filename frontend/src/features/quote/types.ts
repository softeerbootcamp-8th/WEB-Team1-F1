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
  transmission: Transmission
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
  MERCEDES_BENZ: '메르세데스-벤츠',
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
