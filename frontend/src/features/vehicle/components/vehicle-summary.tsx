import type { ReactNode } from 'react'

import {
  FUEL_TYPE_LABEL,
  MANUFACTURER_LABEL,
} from '@/features/quote/types'
import type { VehicleLookupResponse } from '@/features/vehicle/types'
import { formatMileage } from '@/lib/format'

interface VehicleSummaryProps {
  vehicle: VehicleLookupResponse & { mileage?: number }
  children?: ReactNode
  childrenClassName?: string
  specsBelowVehicle?: boolean
  balancedRows?: boolean
}

export function VehicleSummary({
  vehicle,
  children,
  childrenClassName = 'mt-auto pt-6',
  specsBelowVehicle = false,
  balancedRows = false,
}: VehicleSummaryProps) {
  const specifications = (
    <dl
      className={`grid gap-x-10 gap-y-4 text-base sm:grid-cols-2${balancedRows ? ' md:row-span-2 md:grid-rows-2 md:content-between md:gap-y-0' : ''}`}
    >
      <div className="flex items-baseline gap-5">
        <dt className="text-muted-foreground w-24 shrink-0">차량번호</dt>
        <dd className="tabular font-semibold">{vehicle.plateNumber}</dd>
      </div>
      {vehicle.mileage !== undefined ? (
        <div className="flex items-baseline gap-5">
          <dt className="text-muted-foreground w-24 shrink-0">주행거리</dt>
          <dd className="tabular font-semibold">
            {formatMileage(vehicle.mileage)}
          </dd>
        </div>
      ) : (
        <div aria-hidden />
      )}
      <div className="flex items-baseline gap-5">
        <dt className="text-muted-foreground w-24 shrink-0">연식</dt>
        <dd className="tabular font-semibold">{vehicle.modelYear}년</dd>
      </div>
      <div className="flex items-baseline gap-5">
        <dt className="text-muted-foreground w-24 shrink-0">연료</dt>
        <dd className="tabular font-semibold">
          {FUEL_TYPE_LABEL[vehicle.fuelType]}
        </dd>
      </div>
    </dl>
  )

  return (
    <div>
      <div className="flex flex-col gap-4 md:grid md:grid-cols-2">
        <div className="min-w-0">
          <p className="text-lg font-semibold">
            {MANUFACTURER_LABEL[vehicle.manufacturer]} {vehicle.model}
          </p>
          {vehicle.mainImageUrl && (
            <img
              src={vehicle.mainImageUrl}
              alt={vehicle.model}
              className="mt-3 aspect-video w-full rounded-lg object-cover"
            />
          )}
          {specsBelowVehicle && <div className="mt-6">{specifications}</div>}
        </div>
        <div
          className={`min-w-0 flex-1 md:mt-10${balancedRows ? ' flex flex-col md:grid md:grid-rows-4' : ' flex flex-col'}`}
        >
          {!specsBelowVehicle && specifications}
          {children && (
            <div
              className={`${childrenClassName}${balancedRows ? specsBelowVehicle ? ' md:row-span-4' : ' md:row-span-2' : ''}`}
            >
              {children}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
